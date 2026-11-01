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
 * Por cada identificador se guarda cuantos fallos seguidos lleva y cuando
 * empezo la serie. Dos reglas:
 * <ul>
 *   <li>Si entre un fallo y el siguiente pasa mas que la ventana, la serie se
 *       descarta y vuelve a empezar en uno. Asi un fallo suelto cada tanto
 *       --alguien que se equivoca de verdad-- nunca llega a bloquear.</li>
 *   <li>Al alcanzar el limite dentro de la ventana, el identificador queda
 *       bloqueado durante el enfriamiento. Durante ese rato se rechaza
 *       <strong>antes</strong> de consultar la base y antes de comparar la
 *       clave, asi que tampoco sirve para medir tiempos.</li>
 * </ul>
 * Un login exitoso borra el registro.
 *
 * <h2>Lo que cuenta como un mismo usuario</h2>
 * La clave del mapa es lo que la persona escribio en el campo usuario,
 * normalizado a minusculas. El login acepta correo o nombre de usuario, asi
 * que {@code admin.demo} y {@code admin.demo@prueba.local} son la misma cuenta
 * pero <strong>dos entradas distintas</strong> en el mapa, y cada una lleva su
 * propia cuenta de fallos. Resolver eso obligaria a consultar la base antes de
 * aplicar el freno, que es justo lo que el freno quiere evitar. El efecto
 * practico es que un atacante tiene el doble de intentos si conoce las dos
 * formas; sigue siendo un limite, y queda anotado.
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
     * Fallos seguidos de un identificador.
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
     * Corta el intento si el identificador esta en enfriamiento.
     * <p>
     * Se llama al principio del login, antes de tocar la base: durante el
     * bloqueo la respuesta es la misma con clave correcta y con clave
     * equivocada.
     *
     * @throws DemasiadosIntentosException si queda enfriamiento por cumplir
     */
    public void verificarQuePuedeIntentar(String identificador) {
        Serie serie = series.get(clave(identificador));
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
        series.remove(clave(identificador));
    }

    /** Suma un fallo y, si se alcanzo el limite dentro de la ventana, bloquea. */
    public void registrarFallo(String identificador) {
        String clave = clave(identificador);
        Instant ahora = reloj.instant();

        if (series.size() > ENTRADAS_ANTES_DE_LIMPIAR) {
            limpiar();
        }

        Serie resultado = series.compute(clave, (sinUsar, actual) -> {
            if (actual == null || seVencio(actual, ahora)) {
                return new Serie(1, ahora, null);
            }

            int fallos = actual.fallos() + 1;
            Instant bloqueo = fallos >= maxIntentos ? ahora.plus(enfriamiento) : null;

            return new Serie(fallos, actual.inicio(), bloqueo);
        });

        if (resultado.bloqueadoHasta() != null) {
            // Sin el identificador: el log de la aplicacion no es sitio para ir
            // dejando los usuarios o correos que alguien estuvo probando.
            log.warn("Login bloqueado por {} intentos fallidos; enfriamiento de {} minutos",
                    resultado.fallos(), enfriamiento.toMinutes());
        }
    }

    /** Un login correcto borra lo acumulado. */
    public void registrarExito(String identificador) {
        series.remove(clave(identificador));
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

    /**
     * Minusculas y sin espacios, igual que la busqueda del login, que es
     * {@code IgnoreCase}: si no, "Admin.Demo" y "admin.demo" contarian aparte
     * y el limite se esquivaria cambiando una mayuscula.
     */
    private static String clave(String identificador) {
        return identificador == null ? "" : identificador.trim().toLowerCase(Locale.ROOT);
    }
}
