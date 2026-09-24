package sv.edu.ues.fmp.flora.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import sv.edu.ues.fmp.flora.dto.response.PartePlantaResponse;
import sv.edu.ues.fmp.flora.entity.PartePlanta;


@Repository
public interface PartePlantaRepository extends JpaRepository<PartePlanta, Long> {
// spring genera la consulta SQL a partir del nombre

    //find all
    // findById
    //save
    //deleteByID
    //count
    //existById

    // Comprueba si ya hay una parte con ese nombre, sin distinguir mayúsculas
    boolean existsByNombreIgnoreCase(String nombre);

    // Recupera la parte con ese nombre para poder comparar su id al actualizar
    Optional<PartePlanta> findByNombreIgnoreCase(String nombre);

    //** Solo las partes vigentes, es decir las que no han sido dadas de baja logica
    List<PartePlanta> findByActivoTrue();

    /**
     * Busqueda parcial por nombre, sin distinguir mayusculas: "ho" encuentra
     * "Hoja". Es distinta de {@link #findByNombreIgnoreCase}, que busca la
     * coincidencia exacta para detectar duplicados; esta sirve al usuario que
     * escribe en un buscador y por eso devuelve una lista.
     * <p>
     * Genera {@code lower(nombre) like lower(?) escape '\'} con comodines a
     * ambos lados. La tabla solo tiene el UNIQUE {@code parte_planta_nombre_key}
     * sobre la columna tal cual, no un indice funcional sobre {@code lower(...)},
     * asi que este LIKE se resuelve con un recorrido secuencial. Es asumible
     * porque {@code parte_planta} es un catalogo de pocas filas; si algun dia
     * crece, la solucion es un indice trigram (pg_trgm) en la base, no cambiar
     * esta consulta.
     */
    List<PartePlanta> findByNombreContainingIgnoreCase(String nombre);

    //@Query en la misma interfaz usando JPQL solos si no alcanza entoncee se hace con SQL

    //Cuando la consulta no cabe en un nombre. Sigue siendo la interfaz, no hace falta ninguna clase de impl

    @Query("SELECT p FROM PartePlanta p WHERE p.activo = true AND LENGTH(p.nombre) > :min")
    List<PartePlanta> buscarActivasConNombreLargo(@Param("min") int min);


}
