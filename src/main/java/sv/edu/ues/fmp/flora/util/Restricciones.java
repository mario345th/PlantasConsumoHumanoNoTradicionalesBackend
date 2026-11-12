package sv.edu.ues.fmp.flora.util;

import java.util.Locale;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Reconoce QUE restriccion de la base rechazo una escritura.
 *
 * <h2>Para que hace falta</h2>
 * {@code GlobalExceptionHandler} ya convierte cualquier
 * {@link DataIntegrityViolationException} en un 409, pero con un mensaje
 * generico ("La operacion viola una restriccion de la base de datos") que no
 * le dice al cliente que corregir. Los servicios que se anticipan a una
 * restriccion concreta --el titulo de una fuente, la clasificacion de una
 * taxonomia-- necesitan distinguir "choco con la restriccion que yo ya se
 * explicar" de "choco con otra cosa", para traducir la primera a un
 * {@code RecursoDuplicadoException} con un texto util y dejar pasar la segunda
 * al manejador generico.
 *
 * <h2>Por que una comprobacion por partida doble</h2>
 * Primero se intenta por {@link ConstraintViolationException#getConstraintName()},
 * que es el dato limpio que Hibernate extrae del error del driver. No siempre
 * esta: segun el dialecto y el tipo de violacion puede venir {@code null}. Por
 * eso, si falla, se recorre el texto de los mensajes de la cadena de causas
 * buscando el nombre de la restriccion.
 * <p>
 * El texto sirve porque PostgreSQL nombra la restriccion dentro del propio
 * mensaje ({@code llave duplicada viola restriccion de unicidad
 * «uk_taxonomia»}), y el nombre va literal aunque el resto del mensaje este
 * traducido al idioma del servidor. Depender solo del mensaje seria fragil;
 * usarlo como respaldo del nombre no lo es.
 */
public final class Restricciones {

    /**
     * Tope de causas que se recorren. La cadena real tiene tres o cuatro
     * eslabones (Spring -> Hibernate -> driver), asi que el tope solo esta
     * para que una causa que se referencie a si misma no cuelgue el hilo.
     */
    private static final int MAX_CAUSAS = 10;

    private Restricciones() {
    }

    /**
     * Si el error lo provoco la restriccion indicada.
     *
     * @param error  la excepcion que lanzo el repositorio al guardar o al
     *               hacer flush
     * @param nombre nombre de la restriccion o del indice unico tal como esta
     *               en la base, por ejemplo {@code uk_taxonomia}
     */
    public static boolean violacionDe(DataIntegrityViolationException error, String nombre) {
        String buscado = nombre.toLowerCase(Locale.ROOT);

        Throwable actual = error;
        for (int i = 0; actual != null && i < MAX_CAUSAS; i++) {
            if (actual instanceof ConstraintViolationException violacion
                    && nombre.equalsIgnoreCase(violacion.getConstraintName())) {
                return true;
            }

            String mensaje = actual.getMessage();
            if (mensaje != null && mensaje.toLowerCase(Locale.ROOT).contains(buscado)) {
                return true;
            }

            Throwable causa = actual.getCause();
            actual = (causa == actual) ? null : causa;
        }

        return false;
    }
}
