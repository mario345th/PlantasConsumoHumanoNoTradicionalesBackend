package sv.edu.ues.fmp.flora.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import sv.edu.ues.fmp.flora.exception.DemasiadosIntentosException;
import sv.edu.ues.fmp.flora.service.ControlDeIntentosDeLogin.Cuenta;

/**
 * Pruebas del freno a la fuerza bruta, sin Spring y sin base de datos.
 * <p>
 * El componente recibe un {@link Clock} por su constructor de paquete
 * precisamente para esto: comprobar que el enfriamiento termina y que la
 * ventana caduca sin tener que esperar quince minutos de verdad.
 * <p>
 * Las pruebas cuentan contra una {@link Cuenta} ya resuelta --un id-- porque es
 * lo que hace el login desde que se corrigio SEG-H07. Que el correo y el nombre
 * de usuario de una misma persona lleguen a la misma cuenta no se puede
 * comprobar aqui, porque la resolucion ocurre en
 * {@code UsuarioServiceImpl.iniciarSesion} contra la base; eso lo cubre
 * {@code FrenoDeLoginTest} desde HTTP.
 */
class ControlDeIntentosDeLoginTest {

    private static final int MAX_INTENTOS = 5;
    private static final long VENTANA_MINUTOS = 15;
    private static final long ENFRIAMIENTO_MINUTOS = 15;

    /**
     * Ids con un numero que no puede aparecer por casualidad en el mensaje del
     * bloqueo, que habla de minutos: asi
     * {@link #elMensajeNoRepiteElIdentificador()} comprueba algo de verdad.
     */
    private static final long ID_USUARIO = 4242L;

    private static final Cuenta CUENTA = Cuenta.deUsuario(ID_USUARIO);
    private static final Cuenta OTRA_CUENTA = Cuenta.deUsuario(4343L);

    /** Reloj que avanza solo cuando la prueba se lo pide. */
    private static final class RelojControlado extends Clock {

        private Instant ahora = Instant.parse("2026-11-01T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }
    }

    private final RelojControlado reloj = new RelojControlado();

    private final ControlDeIntentosDeLogin control = new ControlDeIntentosDeLogin(
            MAX_INTENTOS, VENTANA_MINUTOS, ENFRIAMIENTO_MINUTOS, reloj);

    private void fallar(int veces) {
        fallar(CUENTA, veces);
    }

    private void fallar(Cuenta cuenta, int veces) {
        for (int i = 0; i < veces; i++) {
            control.registrarFallo(cuenta);
        }
    }

    @Test
    void porDebajoDelLimite_dejaSeguirIntentando() {
        fallar(MAX_INTENTOS - 1);

        assertDoesNotThrow(() -> control.verificarQuePuedeIntentar(CUENTA));
    }

    @Test
    void alLlegarAlLimite_bloquea() {
        fallar(MAX_INTENTOS);

        DemasiadosIntentosException error = assertThrows(DemasiadosIntentosException.class,
                () -> control.verificarQuePuedeIntentar(CUENTA));

        assertTrue(error.getMessage().startsWith("Demasiados intentos fallidos"),
                "el mensaje deberia explicar el bloqueo: " + error.getMessage());
        assertTrue(error.getMessage().contains("minuto"),
                "el mensaje deberia decir cuanto falta: " + error.getMessage());
    }

    /**
     * Lo que hace util al bloqueo: mientras dura, da igual que la clave sea
     * correcta. El control corre antes de comparar nada, asi que ni se llega a
     * mirar.
     */
    @Test
    void duranteElEnfriamiento_bloqueaAunqueLaClaveFueraCorrecta() {
        fallar(MAX_INTENTOS);

        reloj.avanzar(Duration.ofMinutes(ENFRIAMIENTO_MINUTOS - 1));

        assertThrows(DemasiadosIntentosException.class,
                () -> control.verificarQuePuedeIntentar(CUENTA));
    }

    @Test
    void cumplidoElEnfriamiento_vuelveADejarIntentar() {
        fallar(MAX_INTENTOS);

        reloj.avanzar(Duration.ofMinutes(ENFRIAMIENTO_MINUTOS + 1));

        assertDoesNotThrow(() -> control.verificarQuePuedeIntentar(CUENTA));
    }

    @Test
    void unExitoAntesDelLimite_borraLoAcumulado() {
        fallar(MAX_INTENTOS - 1);
        control.registrarExito(CUENTA);

        // Si el contador no se hubiera reiniciado, este unico fallo seria el
        // quinto y bloquearia.
        control.registrarFallo(CUENTA);

        assertDoesNotThrow(() -> control.verificarQuePuedeIntentar(CUENTA));
    }

    /**
     * Fallos sueltos y separados en el tiempo son alguien que se equivoca, no
     * una rafaga: la serie caduca con la ventana y nunca llega al limite.
     */
    @Test
    void fallosSeparadosPorMasQueLaVentana_noBloquean() {
        for (int i = 0; i < MAX_INTENTOS * 3; i++) {
            control.registrarFallo(CUENTA);
            reloj.avanzar(Duration.ofMinutes(VENTANA_MINUTOS + 1));
            assertDoesNotThrow(() -> control.verificarQuePuedeIntentar(CUENTA),
                    "un fallo cada " + (VENTANA_MINUTOS + 1) + " minutos no deberia bloquear");
        }
    }

    @Test
    void elBloqueoDeUnUsuarioNoAfectaAOtro() {
        fallar(MAX_INTENTOS);

        assertThrows(DemasiadosIntentosException.class,
                () -> control.verificarQuePuedeIntentar(CUENTA));
        assertDoesNotThrow(() -> control.verificarQuePuedeIntentar(OTRA_CUENTA));
    }

    /**
     * SEG-H07: una cuenta resuelta se identifica por su id y por nada mas, asi
     * que da igual con que texto se haya llegado a ella. Dos {@code Cuenta} del
     * mismo usuario son la misma entrada del contador.
     */
    @Test
    void laCuentaResueltaNoDependeDelTextoEscrito() {
        // Como si los cinco fallos hubieran entrado unos por el nombre de
        // usuario y otros por el correo: el login resuelve los dos al mismo id
        // antes de contar.
        for (int i = 0; i < MAX_INTENTOS; i++) {
            control.registrarFallo(Cuenta.deUsuario(7L));
        }

        assertThrows(DemasiadosIntentosException.class,
                () -> control.verificarQuePuedeIntentar(Cuenta.deUsuario(7L)));
    }

    /**
     * Un texto sin resolver y un id no comparten espacio de nombres: quien
     * escriba "7" no puede bloquear al usuario con id 7.
     */
    @Test
    void elTextoSinResolverNoColisionaConUnId() {
        fallar(Cuenta.noResuelta("7"), MAX_INTENTOS);

        assertThrows(DemasiadosIntentosException.class,
                () -> control.verificarQuePuedeIntentar(Cuenta.noResuelta("7")));
        assertDoesNotThrow(() -> control.verificarQuePuedeIntentar(Cuenta.deUsuario(7L)));
    }

    /**
     * La busqueda del login es {@code IgnoreCase}, asi que el contador del
     * texto sin resolver tambien: si no, el limite se esquivaria cambiando una
     * mayuscula.
     */
    @Test
    void elContadorDelTextoNoDistingueMayusculasNiEspacios() {
        control.registrarFallo(Cuenta.noResuelta("no.existe"));
        control.registrarFallo(Cuenta.noResuelta("NO.EXISTE"));
        control.registrarFallo(Cuenta.noResuelta("  No.Existe  "));
        control.registrarFallo(Cuenta.noResuelta("no.EXISTE"));
        control.registrarFallo(Cuenta.noResuelta("nO.eXiStE"));

        assertThrows(DemasiadosIntentosException.class,
                () -> control.verificarQuePuedeIntentar(Cuenta.noResuelta("no.existe")));
    }

    /** Contar por cuenta exige el id; pasar null es un error de programacion. */
    @Test
    void cuentaDeUsuario_sinId_noSeConstruye() {
        assertThrows(IllegalArgumentException.class, () -> Cuenta.deUsuario(null));
    }

    @Test
    void elMensajeNoRepiteElIdentificador() {
        fallar(MAX_INTENTOS);

        DemasiadosIntentosException error = assertThrows(DemasiadosIntentosException.class,
                () -> control.verificarQuePuedeIntentar(CUENTA));

        assertTrue(!error.getMessage().contains(String.valueOf(ID_USUARIO)),
                "el mensaje no deberia devolver el identificador: " + error.getMessage());
    }
}
