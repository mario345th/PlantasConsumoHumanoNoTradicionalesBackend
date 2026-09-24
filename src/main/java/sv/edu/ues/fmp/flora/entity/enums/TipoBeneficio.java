package sv.edu.ues.fmp.flora.entity.enums;

/**
 * Tipo o categoria a la que pertenece un beneficio.
 * Se persiste como texto en la columna {@code beneficio.tipo_beneficio} (varchar(20)),
 * protegida por un CHECK constraint en la base de datos.
 */

public enum TipoBeneficio {

    NUTRICIONAL,
    FUNCIONAL,
    OTRO
}
