package sv.edu.ues.fmp.flora.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import sv.edu.ues.fmp.flora.entity.NombreComun;

/**
 * Acceso a datos de {@link NombreComun}.
 * <p>
 * Todas las consultas arrancan por la especie porque un nombre comun solo
 * tiene sentido dentro de una: no hay listados globales.
 * <p>
 * Las dos consultas de duplicados comparan en {@code lower(...)} porque la
 * restriccion real de la base es el indice unico <em>funcional</em>
 * {@code uk_nombre_comun_especie_lower}, construido sobre
 * ({@code id_especie}, {@code lower(nombre)}, {@code coalesce(lower(region), '')}).
 * Recuerdese que la bandera de estado de esta tabla es {@code activo}, en
 * masculino.
 */
@Repository
public interface NombreComunRepository extends JpaRepository<NombreComun, Long> {

    /** Todos los nombres de una especie, activos e inactivos. */
    List<NombreComun> findByEspecieIdEspecie(Long idEspecie);

    List<NombreComun> findByEspecieIdEspecieAndActivoTrue(Long idEspecie);

    /**
     * El unico nombre principal y activo que la especie puede tener.
     * Devuelve como maximo una fila porque asi lo garantiza el indice unico
     * parcial {@code uk_nombre_comun_principal}.
     */
    Optional<NombreComun> findByEspecieIdEspecieAndEsPrincipalTrueAndActivoTrue(Long idEspecie);

    /**
     * Deteccion de duplicados antes de insertar, para devolver un 409 legible.
     * <p>
     * La region forma parte de la comparacion porque asi lo hace el indice
     * {@code uk_nombre_comun_especie_lower}: un mismo nombre puede repetirse
     * dentro de la especie si se usa en regiones distintas. Un metodo derivado
     * como {@code existsBy...AndNombreIgnoreCaseAndRegionIgnoreCase} no sirve,
     * porque generaria {@code region = null} cuando el nombre no lleva region, y
     * en SQL esa comparacion nunca es verdadera: dos nombres sin region jamas se
     * detectarian como duplicados. De ahi la doble condicion sobre
     * {@code region}, que replica la semantica del {@code coalesce(..., '')} del
     * indice.
     * <p>
     * El {@code CAST(:region AS string)} no es decorativo: es el mismo problema
     * que documenta {@link TaxonomiaRepository#buscarPorClasificacion}. Sin el,
     * al comparar el parametro contra {@code IS NULL} Hibernate no puede inferir
     * su tipo y lo envia como {@code bytea}, el tipo binario generico de
     * PostgreSQL; el {@code lower(?)} de la rama siguiente falla entonces en
     * ejecucion con {@code function lower(bytea) does not exist}. El CAST
     * declara el tipo y Hibernate emite {@code cast(? as varchar)}. Ni el
     * compilador ni la validacion de arranque detectan ese fallo: solo aparece
     * al ejecutar con {@code region} null.
     */
    @Query("""
        SELECT count(n) > 0 FROM NombreComun n
        WHERE n.especie.idEspecie = :idEspecie
          AND lower(n.nombre) = lower(:nombre)
          AND ((CAST(:region AS string) IS NULL AND n.region IS NULL)
               OR lower(n.region) = lower(CAST(:region AS string)))
        """)
    boolean existeNombreEnEspecie(@Param("idEspecie") Long idEspecie,
                                  @Param("nombre") String nombre,
                                  @Param("region") String region);

    /**
     * Misma comparacion que {@link #existeNombreEnEspecie}, pero devolviendo la
     * entidad: al actualizar hay que comparar el id, porque encontrar el propio
     * registro que se esta editando no es un duplicado y debe dejar guardar.
     * Esa exclusion la aplica el servicio con un {@code filter} por id.
     * <p>
     * Sobre el {@code CAST(:region AS string)} y la doble condicion de nulos,
     * ver la explicacion de {@link #existeNombreEnEspecie}.
     */
    @Query("""
        SELECT n FROM NombreComun n
        WHERE n.especie.idEspecie = :idEspecie
          AND lower(n.nombre) = lower(:nombre)
          AND ((CAST(:region AS string) IS NULL AND n.region IS NULL)
               OR lower(n.region) = lower(CAST(:region AS string)))
        """)
    Optional<NombreComun> buscarPorNombreYRegion(@Param("idEspecie") Long idEspecie,
                                                 @Param("nombre") String nombre,
                                                 @Param("region") String region);

    /** Sirve a la regla que impide dejar huerfanos a los nombres alternativos. */
    long countByEspecieIdEspecieAndActivoTrue(Long idEspecie);
}
