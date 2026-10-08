package sv.edu.ues.fmp.flora.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import sv.edu.ues.fmp.flora.entity.PreparacionConsumo;

public interface PreparacionConsumoRepository extends JpaRepository<PreparacionConsumo, Long> {
    List<PreparacionConsumo> findAllByOrderByOrdenPresentacionAscIdPreparacionAsc();
    List<PreparacionConsumo> findByActivaTrueOrderByOrdenPresentacionAscIdPreparacionAsc();
    List<PreparacionConsumo> findByEspecieParteComestibleIdEspecieParteOrderByOrdenPresentacionAscIdPreparacionAsc(Long idParte);
    List<PreparacionConsumo> findByEspecieParteComestibleIdEspecieParteAndActivaTrueOrderByOrdenPresentacionAscIdPreparacionAsc(Long idParte);
    boolean existsByEspecieParteComestibleIdEspecieParteAndNombreIgnoreCase(Long idParte, String nombre);
    boolean existsByEspecieParteComestibleIdEspecieParteAndNombreIgnoreCaseAndIdPreparacionNot(Long idParte, String nombre, Long id);
}
