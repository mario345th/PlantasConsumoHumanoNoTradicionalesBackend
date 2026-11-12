package sv.edu.ues.fmp.flora.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import sv.edu.ues.fmp.flora.exception.DemasiadosIntentosException;

/**
 * Freno a la fuerza bruta en el login: cuenta los intentos fallidos por
 * usuario y, pasado el limite, deja de atenderlos durante un enfriamiento.
 *
 * <h2>Por que hacia falta</h2>
 * Hay dos endpoints de login publicos y ninguno limitaba nada: se podian
 * probar claves contra una cuenta conocida a la velocidad que diera la red.
 * BCrypt con factor 10 cuesta unos 50 ms por intento, que frena a un atacante
 * ingenuo pero no a uno que paralelice.
 *
 * <h2>Como cuenta</h2>
 * Por cada cuenta se guarda cuantos fallos seguidos lleva y cuando empezo la
 * serie. Dos reglas:
 * <ul>
 *   <li>Si entre un fallo y el siguiente pasa mas que la ventana, la serie se
 *       descarta y vuelve a empezar en uno. Asi un fallo suelto cada tanto
 *       --alguien que se equivoca de verdad-- nunca llega a bloquear.</li>
 *   <li>Al alcanzar el limite dentro de la ventana, la cuenta queda bloqueada
 *       durante el enfriamiento. Durante ese rato se rechaza
 *       <strong>antes</strong> de comparar la clave, asi que tampoco sirve
 *       para medir tiempos.</li>
 * </ul>
 * Un login exitoso borra el registro.
 *
 * <h2>Lo que cuenta como un mismo usuario (SEG-H07)</h2>
 * La clave del mapa es el <strong>id del usuario</strong>, no lo que la
 * persona escribio. Antes era el texto normalizado a minusculas, y como el
 * login acepta indistintamente el correo o el nombre de usuario,
 * {@code admin.demo} y {@code admin.demo@prueba.local} eran dos entradas
 * distintas con su propia cuenta de fallos: quien agotaba los cinco intentos
 * con el nombre de usuario seguia teniendo otros cinco escribiendo el correo,
 * y el bloqueo se esquivaba alternando las dos formas. Con la cuenta ya
 * resuelta, las dos formas suman al mismo contador.
 * <p>
 * Para un texto que no corresponde a ninguna cuenta no hay id que usar, asi
 * que esos intentos se siguen contando por el texto normalizado --ver
 * {@link Cuenta#noResuelta(String)}--. Hace falta: si no se contaran, probar
 * usuarios inventados seria gratis y el limite solo aplicaria a las cuentas
 * que existen.
 *
 * <h2>El precio: resolver la cuenta cuesta una consulta</h2>
 * Contar por id obliga a buscar al usuario <strong>antes</strong> de aplicar
 * el freno, de modo que durante el enfriamiento la peticion ya no se rechaza
 * sin tocar la base: hace un SELECT por correo o nombre de usuario, los dos
 * con indice unico. Lo que se sigue cumpliendo, y es lo que importa, es que el
 * rechazo ocurre antes de comparar la clave con BCrypt, que es la parte cara y
 * la unica que distinguiria una clave correcta de una equivocada. Ver
 * {@code UsuarioServiceImpl.iniciarSesion}.
 * <p>
 * No se cuenta por IP. Habria que meter la peticion HTTP en la capa de
 * servicio, que es donde vive el freno para cubrir los dos endpoints de login
 * de una vez. Con el despliegue detras de un proxy ademas haria falta resolver
 * {@code X-Forwarded-For} para no acabar contando todo el trafico contra una
 * sola IP. Queda para cuando el despliegue este definido.
 *
 * <h2>Es memoria de un solo proceso</h2>
 * El mapa vive en el heap de esta instancia. Las consecuencias, para que nadie
 * se lleve una sorpresa:
 * <ul>
 *   <li><strong>Reiniciar la aplicacion borra los bloqueos.</strong></li>
 *   <li><strong>Con varias instancias detras de un balanceador el limite se
 *       multiplica por el numero de instancias</strong>, porque cada una cuenta
 *       lo suyo. Para que el limite sea real haria falta un contador
 *       compartido (Redis o una tabla), y eso es una dependencia nueva que esta
 *       tarea no mete.</li>
 * </ul>
 * Hoy el despliegue es una sola instancia, asi que el freno sirve tal cual.
 */
@Component
public class ControlDeIntentosDeLogin {

    private static final Logger log = LoggerFactory.getLogger(ControlDeIntentosDeLogin.class);

    /**
     * Cuantas entradas se toleran antes de hacer limpieza.
     * <p>
     * El mapa solo crece cuando alguien falla, y las entradas se borran al
     * acertar o al caducar, pero nada impide que un atacante pruebe un usuario
     * distinto cada vez y llene memoria. El barrido de {@link #limpiar()} corre
     * cuando se pasa de este tamaño, no con un temporizador, para no sumar un
     * hilo al proyecto por esto.
     */
    private static final int ENTRADAS_ANTES_DE_LIMPIAR = 10_000;

    private final int maxIntentos;
    private final Duration ventana;
    private final Duration enfriamiento;
    private final Clock reloj;

    private final Map<String, Serie> series = new ConcurrentHashMap<>();

    /**
     * Contra quien se cuentan los intentos.
     *
     * <h2>Por que un tipo y no un String</h2>
     * Las tres operaciones de esta clase se llaman con lo mismo, y si recibieran
     * un {@code String} nada impediria volver a pasarles el texto que escribio
     * la persona --que es justo el fallo SEG-H07 que se arreglo--. Con este tipo
     * hay que decir explicitamente si se cuenta por cuenta resuelta
     * ({@link #deUsuario}) o por texto sin resolver ({@link #noResuelta}), y el
     * prefijo de la clave mantiene los dos espacios de nombres separados: un
     * usuario con id 7 no puede colisionar con alguien que escriba "7".
     *
     * @param clave la clave del mapa; no se expone fuera de este componente ni
     *              aparece en ningun mensaje
     */
    public record Cuenta(String clave) {

        /** Intentos contra una cuenta que existe, se haya entrado por correo o por usuario. */
        public static Cuenta deUsuario(Long idUsuario) {
            if (idUsuario == null) {
                throw new IllegalArgumentException(
                        "Para contar por cuenta hace falta el id; use Cuenta.noResuelta");
            }
            return new Cuenta("id:" + idUsuario);
        }

        /**
         * Intentos contra un texto que no corresponde a ninguna cuenta.
         * <p>
         * Se normaliza a minusculas y sin espacios en los extremos, igual que
         * la busqueda del login, que es {@code IgnoreCase}: si no, "Noexiste" y
         * "noexiste" contarian aparte y el limite se esquivaria cambiando una
         * mayuscula.
         */
        public static Cuenta noResuelta(String identificadorEscrito) {
            String normalizado = identificadorEscrito == null
                    ? ""
                    : identificadorEscrito.trim().toLowerCase(Locale.ROOT);

            return new Cuenta("txt:" + normalizado);
        }
    }

    /**
     * Fallos seguidos de una cuenta.
     *
     * @param fallos          cuantos lleva
     * @param inicio          cuando empezo la serie, para medir la ventana
     * @param bloqueadoHasta  fin del enfriamiento, o {@code null} si no esta
     *                        bloqueado
     */
    private record Serie(int fallos, Instant inicio, Instant bloqueadoHasta) {
    }

    @Autowired
    public ControlDeIntentosDeLogin(
            @Value("${app.login.max-intentos:5}") int maxIntentos,
            @Value("${app.login.ventana-minutos:15}") long ventanaMinutos,
            @Value("${app.login.enfriamiento-minutos:15}") long enfriamientoMinutos) {

        this(maxIntentos, ventanaMinutos, enfriamientoMinutos, Clock.systemUTC());
    }

    /**
     * Para las pruebas: el mismo componente con un reloj que ellas controlan.
     * Sin esto, comprobar que el enfriamiento termina pediria esperar quince
     * minutos de verdad.
     */
    ControlDeIntentosDeLogin(
            int maxIntentos, long ventanaMinutos, long enfriamientoMinutos, Clock reloj) {

        this.maxIntentos = maxIntentos;
        this.ventana = Duration.ofMinutes(ventanaMinutos);
        this.enfriamiento = Duration.ofMinutes(enfriamientoMinutos);
        this.reloj = reloj;
    }

    /**
     * Corta el intento si la cuenta esta en enfriamiento.
     * <p>
     * Se llama antes de comparar la clave: durante el bloqueo la respuesta es
     * la misma con clave correcta y con clave equivocada.
     *
     * @throws DemasiadosIntentosException si queda enfriamiento por cumplir
     */
    public void verificarQuePuedeIntentar(Cuenta cuenta) {
        Serie serie = series.get(cuenta.clave());
        if (serie == null || serie.bloqueadoHasta() == null) {
            return;
        }

        Instant ahora = reloj.instant();
        if (ahora.isBefore(serie.bloqueadoHasta())) {
            long minutos = Math.max(1, Duration.between(ahora, serie.bloqueadoHasta()).toMinutes());

            throw new DemasiadosIntentosException(
                    "Demasiados intentos fallidos. Vuelva a intentarlo en "
                            + minutos + (minutos == 1 ? " minuto" : " minutos") + ".");
        }

        // El enfriamiento ya paso: se limpia aqui en vez de dejar la entrada
        // caducada ocupando sitio.
        series.remove(cuenta.clave());
    }

    /** Suma un fallo y, si se alcanzo el limite dentro de la ventana, bloquea. */
    public void registrarFallo(Cuenta cuenta) {
        Instant ahora = reloj.instant();

        if (series.size() > ENTRADAS_ANTES_DE_LIMPIAR) {
            limpiar();
        }

        Serie resultado = series.compute(cuenta.clave(), (sinUsar, actual) -> {
            if (actual == null || seVencio(actual, ahora)) {
                return new Serie(1, ahora, null);
            }

            int fallos = actual.fallos() + 1;
            Instant bloqueo = fallos >= maxIntentos ? ahora.plus(enfriamiento) : null;

            return new Serie(fallos, actual.inicio(), bloqueo);
        });

        if (resultado.bloqueadoHasta() != null) {
            // Sin la cuenta: el log de la aplicacion no es sitio para ir
            // dejando los usuarios o correos que alguien estuvo probando.
            log.warn("Login bloqueado por {} intentos fallidos; enfriamiento de {} minutos",
                    resultado.fallos(), enfriamiento.toMinutes());
        }
    }

    /** Un login correcto borra lo acumulado. */
    public void registrarExito(Cuenta cuenta) {
        series.remove(cuenta.clave());
    }

    /**
     * Si la serie empezo hace mas que la ventana y no hay bloqueo vigente, no
     * cuenta: son fallos sueltos, no una rafaga.
     */
    private boolean seVencio(Serie serie, Instant ahora) {
        if (serie.bloqueadoHasta() != null) {
            return !ahora.isBefore(serie.bloqueadoHasta());
        }
        return ahora.isAfter(serie.inicio().plus(ventana));
    }

    /** Descarta las entradas que ya no frenan nada. */
    private void limpiar() {
        Instant ahora = reloj.instant();
        series.values().removeIf(serie -> seVencio(serie, ahora));
    }
}
