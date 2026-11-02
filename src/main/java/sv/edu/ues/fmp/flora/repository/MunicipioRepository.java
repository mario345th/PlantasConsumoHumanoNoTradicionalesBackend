package sv.edu.ues.fmp.flora.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import sv.edu.ues.fmp.flora.entity.Municipio;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MunicipioRepository extends JpaRepository<Municipio, Long> {

    //WHERE m.id_departamento = ? AND lower(m.nombre) = lower(?)
    boolean existsByDepartamentoIdDepartamentoAndNombreIgnoreCase(Long idDepartamento, String nombre);

    // Nos permite ver si realmente hay algo en la lista y no tener un NullPointerException si no trae nada
    Optional<Municipio> findByDepartamentoIdDepartamentoAndNombreIgnoreCase(Long idDepartamento, String nombre);

    List<Municipio> findByActivoTrue();

    List<Municipio> findByDepartamentoIdDepartamento(Long idDepartamento);

    /**
     * Busqueda parcial por nombre, sin distinguir mayusculas: "santa" encuentra
     * "Santa Ana" y "Santa Tecla". Busca en todo el pais, porque el punto de
     * partida de quien busca es el nombre y no el departamento; para filtrar por
     * departamento ya esta {@link #findByDepartamentoIdDepartamento}.
     * <p>
     * Es distinta de {@link #findByDepartamentoIdDepartamentoAndNombreIgnoreCase},
     * que busca la coincidencia exacta dentro de un departamento para detectar
     * duplicados.
     * <p>
     * Genera {@code lower(nombre) like lower(?) escape '\'} con comodines a
     * ambos lados. La tabla solo tiene el UNIQUE
     * {@code uk_municipio_departamento_nombre} sobre (id_departamento, nombre)
     * tal cual, no un indice funcional sobre {@code lower(...)}, asi que este
     * LIKE se resuelve con un recorrido secuencial. Es asumible porque
     * {@code municipio} ronda las 260 filas y no crece.
     */
    List<Municipio> findByNombreContainingIgnoreCase(String nombre);

    /**
     * Nombre del departamento al que pertenece cada municipio pedido.
     * <p>
     * {@code Distrito} guarda {@code id_municipio} como un Long suelto, sin
     * {@code @ManyToOne}, asi que la cadena distrito -> municipio ->
     * departamento no se puede recorrer con asociaciones JPA. Esta consulta
     * cubre el salto que falta y resuelve el hallazgo AN-H05
     * ({@code departamentoDistrito} llegaba siempre en null).
     * <p>
     * Recibe la coleccion completa de municipios de una pagina de resultados
     * para resolverlos en una sola consulta en lugar de una por ubicacion.
     */
    @Query("""
           select m.idMunicipio as idMunicipio, m.departamento.nombre as nombreDepartamento
           from Municipio m
           where m.idMunicipio in :idsMunicipio
           """)
    List<DepartamentoDeMunicipio> findDepartamentosPorMunicipio(Collection<Long> idsMunicipio);

    /** Proyeccion de {@link #findDepartamentosPorMunicipio(Collection)}. */
    interface DepartamentoDeMunicipio {
        Long getIdMunicipio();

        String getNombreDepartamento();
    }
}
