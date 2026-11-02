package sv.edu.ues.fmp.flora.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import sv.edu.ues.fmp.flora.entity.UbicacionEspecie;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface UbicacionEspecieRepository extends JpaRepository<UbicacionEspecie, Long> {

    /**
     * Ubicaciones activas de una especie, con el distrito ya cargado.
     * <p>
     * El {@code join fetch} evita el N+1 que provocaba el mapeo a respuesta al
     * recorrer el distrito perezoso de cada fila, y garantiza que el distrito
     * (y de ahi su municipio) este disponible para resolver el departamento
     * del hallazgo AN-H05.
     */
    @Query("""
           select u from UbicacionEspecie u
           join fetch u.distrito
           where u.especie.idEspecie = :idEspecie and u.activa = true
           order by u.idUbicacion asc
           """)
    List<UbicacionEspecie> findActivasPorEspecieConDistrito(Long idEspecie);

    /**
     * Comprueba si la especie ya tiene una ubicacion activa en esas
     * coordenadas, para devolver un 409 con mensaje propio antes del INSERT.
     * <p>
     * QA reporto (AN-H06) que el duplicado lo detectaba solo el UNIQUE de la
     * base: el mensaje era generico y cada intento rechazado consumia un id de
     * la secuencia (se perdieron los ids 3 a 8). Mira solo las activas, igual
     * que el indice unico parcial {@code uk_ubicacion_especie_coords_activa}.
     */
    boolean existsByEspecieIdEspecieAndLatitudAndLongitudAndActivaTrue(
            Long idEspecie, BigDecimal latitud, BigDecimal longitud);

    /** Igual que el anterior, pero ignorando el propio registro al actualizarlo. */
    boolean existsByEspecieIdEspecieAndLatitudAndLongitudAndActivaTrueAndIdUbicacionNot(
            Long idEspecie, BigDecimal latitud, BigDecimal longitud, Long idUbicacion);
}
