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

/**
 * Pruebas del freno a la fuerza bruta, sin Spring y sin base de datos.
 * <p>
 * El componente recibe un {@link Clock} por su constructor de paquete
 * precisamente para esto: comprobar que el enfriamiento termina y que la
 * ventana caduca sin tener que esperar quince minutos de verdad.
 */
class ControlDeIntentosDeLoginTest {

    private static final int MAX_INTENTOS = 5;
    private static final long VENTANA_MINUTOS = 15;
    private static final long ENFRIAMIENTO_MINUTOS = 15;

    private static final String USUARIO = "admin.demo";

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
        for (int i = 0; i < veces; i++) {
            control.registrarFallo(USUARIO);
        }
    }

    @Test
    void porDebajoDelLimite_dejaSeguirIntentando() {
        fallar(MAX_INTENTOS - 1);

        assertDoesNotThrow(() -> control.verificarQuePuedeIntentar(USUARIO));
    }

    @Test
    void alLlegarAlLimite_bloquea() {
        fallar(MAX_INTENTOS);

        DemasiadosIntentosException error = assertThrows(DemasiadosIntentosException.class,
                () -> control.verificarQuePuedeIntentar(USUARIO));

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
                () -> control.verificarQuePuedeIntentar(USUARIO));
    }

    @Test
    void cumplidoElEnfriamiento_vuelveADejarIntentar() {
        fallar(MAX_INTENTOS);

        reloj.avanzar(Duration.ofMinutes(ENFRIAMIENTO_MINUTOS + 1));

        assertDoesNotThrow(() -> control.verificarQuePuedeIntentar(USUARIO));
    }

    @Test
    void unExitoAntesDelLimite_borraLoAcumulado() {
        fallar(MAX_INTENTOS - 1);
        control.registrarExito(USUARIO);

        // Si el contador no se hubiera reiniciado, este unico fallo seria el
        // quinto y bloquearia.
        control.registrarFallo(USUARIO);

        assertDoesNotThrow(() -> control.verificarQuePuedeIntentar(USUARIO));
    }

    /**
     * Fallos sueltos y separados en el tiempo son alguien que se equivoca, no
     * una rafaga: la serie caduca con la ventana y nunca llega al limite.
     */
    @Test
    void fallosSeparadosPorMasQueLaVentana_noBloquean() {
        for (int i = 0; i < MAX_INTENTOS * 3; i++) {
            control.registrarFallo(USUARIO);
            reloj.avanzar(Duration.ofMinutes(VENTANA_MINUTOS + 1));
            assertDoesNotThrow(() -> control.verificarQuePuedeIntentar(USUARIO),
                    "un fallo cada " + (VENTANA_MINUTOS + 1) + " minutos no deberia bloquear");
        }
    }

    @Test
    void elBloqueoDeUnUsuarioNoAfectaAOtro() {
        fallar(MAX_INTENTOS);

        assertThrows(DemasiadosIntentosException.class,
                () -> control.verificarQuePuedeIntentar(USUARIO));
        assertDoesNotThrow(() -> control.verificarQuePuedeIntentar("investigador.demo"));
    }

    /**
     * La busqueda del login es {@code IgnoreCase}, asi que el contador tambien:
     * si no, el limite se esquivaria cambiando una mayuscula en cada intento.
     */
    @Test
    void elContadorNoDistingueMayusculasNiEspacios() {
        control.registrarFallo("admin.demo");
        control.registrarFallo("ADMIN.DEMO");
        control.registrarFallo("  Admin.Demo  ");
        control.registrarFallo("admin.DEMO");
        control.registrarFallo("AdMiN.dEmO");

        assertThrows(DemasiadosIntentosException.class,
                () -> control.verificarQuePuedeIntentar("admin.demo"));
    }

    @Test
    void elMensajeNoRepiteElIdentificador() {
        fallar(MAX_INTENTOS);

        DemasiadosIntentosException error = assertThrows(DemasiadosIntentosException.class,
                () -> control.verificarQuePuedeIntentar(USUARIO));

        assertTrue(!error.getMessage().contains(USUARIO),
                "el mensaje no deberia devolver el identificador: " + error.getMessage());
    }
}
