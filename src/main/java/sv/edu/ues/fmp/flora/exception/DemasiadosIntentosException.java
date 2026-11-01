package sv.edu.ues.fmp.flora.exception;

/**
 * Se acumularon demasiados intentos de login fallidos para un mismo usuario y
 * la cuenta esta en enfriamiento. La atiende
 * {@code GlobalExceptionHandler.manejarDemasiadosIntentos} con un 429.
 * <p>
 * Es distinta de {@link CredencialesInvalidasException} a proposito: esa dice
 * "esas credenciales no sirven" y esta dice "deja de probar". Mientras el
 * enfriamiento esta activo se responde esto incluso cuando la clave es
 * correcta, porque si no, el 401 y el 200 seguirian distinguiendose y el
 * bloqueo no serviria de nada.
 */
public class DemasiadosIntentosException extends RuntimeException {

    public DemasiadosIntentosException(String mensaje) {
        super(mensaje);
    }
}
