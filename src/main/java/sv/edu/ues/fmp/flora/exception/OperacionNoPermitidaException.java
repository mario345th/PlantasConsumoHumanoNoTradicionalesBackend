package sv.edu.ues.fmp.flora.exception;

/**
 * Quien llama tiene el permiso del endpoint, pero la regla de negocio no le
 * deja hacer esta operacion sobre este usuario (SEG-H09). La atiende
 * {@code GlobalExceptionHandler.manejarOperacionNoPermitida} con un 403.
 *
 * <h2>Por que no se usa {@code AccessDeniedException}</h2>
 * Spring Security tiene su propia excepcion para el acceso denegado y el
 * proyecto ya la traduce a 403, pero con un mensaje fijo --"No tiene los
 * permisos necesarios para realizar esta operacion"-- que es deliberado: ese
 * 403 lo recibe cualquiera que tantee un endpoint y no tiene por que
 * convertirse en un mapa de la autorizacion.
 * <p>
 * Aqui la situacion es otra. Quien llega hasta estas reglas ya paso el
 * {@code @PreAuthorize} del modulo de seguridad, o sea que trae
 * {@code USUARIO_GESTIONAR} y con el ya puede listar usuarios y leer que roles
 * tiene cada uno. Decirle "ese es el superadministrador" o "se quedaria el
 * sistema sin administradores" no le revela nada que no pueda consultar, y es
 * la diferencia entre una pantalla que explica por que no se pudo y una que
 * solo dice 403.
 *
 * <h2>Por que 403 y no 409</h2>
 * Lo que falla es la autorizacion --no eres quien puede hacer esto-- y no el
 * estado de los datos. El unico bloqueo de SEG-H09 que es de estado es el
 * invariante de no quedarse sin administradores activos, y ese se lanza como
 * {@link EstadoInvalidoException} para que salga con 409.
 */
public class OperacionNoPermitidaException extends RuntimeException {

    public OperacionNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
