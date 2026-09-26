package sv.edu.ues.fmp.flora.entity.enums;

/**
 * Categoria a la que pertenece un nutriente.
 * Se persiste como texto en la columna {@code nutriente.categoria} (varchar(20)),
 * protegida por un CHECK constraint en la base de datos.
 */
public enum CategoriaNutriente {
    MACRONUTRIENTE,
    VITAMINA,
    MINERAL,
    ENERGIA,
    FIBRA,
    OTRO

}
