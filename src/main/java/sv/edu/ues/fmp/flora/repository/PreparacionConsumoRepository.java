package sv.edu.ues.fmp.flora.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import sv.edu.ues.fmp.flora.entity.PreparacionConsumo;

public interface PreparacionConsumoRepository extends JpaRepository<PreparacionConsumo, Long> {
    List<PreparacionConsumo> findAllByOrderByOrdenPresentacionAscIdPreparacionAsc();
    List<PreparacionConsumo> findByActivaTrueOrderByOrdenPresentacionAscIdPreparacionAsc();
    List<PreparacionConsumo> findByEspecieParteComestibleIdEspecieParteOrderByOrdenPresentacionAscIdPreparacionAsc(Long idParte);
    List<PreparacionConsumo> findByEspecieParteComestibleIdEspecieParteAndActivaTrueOrderByOrdenPresentacionAscIdPreparacionAsc(Long idParte);

    /**
     * Nombres ya registrados para una parte comestible, incluidas las
     * preparaciones inactivas.
     * <p>
     * Sustituye a los {@code existsBy...NombreIgnoreCase} anteriores: la
     * comparacion de duplicados tambien tiene que ignorar los acentos
     * ("Preparación QA" frente a "Preparacion QA"), y eso no se puede expresar
     * en una consulta derivada sin un indice funcional con {@code unaccent}.
     * Se comparan en el servicio con {@code Textos.claveComparacion}.
     * <p>
     * Trae solo la columna del nombre, y una parte comestible tiene unas pocas
     * preparaciones, asi que el coste es despreciable.
     */
    @Query("""
           select p.nombre from PreparacionConsumo p
           where p.especieParteComestible.idEspecieParte = :idParte
           """)
    List<String> findNombresPorParte(Long idParte);

    /** Igual que el anterior, pero ignorando el propio registro al actualizarlo. */
    @Query("""
           select p.nombre from PreparacionConsumo p
           where p.especieParteComestible.idEspecieParte = :idParte
             and p.idPreparacion <> :idPreparacion
           """)
    List<String> findNombresPorParteExcluyendo(Long idParte, Long idPreparacion);
}
