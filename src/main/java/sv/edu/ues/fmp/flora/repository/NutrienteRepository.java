package sv.edu.ues.fmp.flora.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sv.edu.ues.fmp.flora.entity.Nutriente;
import sv.edu.ues.fmp.flora.entity.enums.CategoriaNutriente;

@Repository
public interface NutrienteRepository extends JpaRepository<Nutriente, Long> {

    boolean existsByNombreIgnoreCase(String nombre);
    Optional<Nutriente> findByNombreIgnoreCase(String nombre);

    List<Nutriente> findAllByOrderByIdNutrienteAsc();
    List<Nutriente> findByActivoTrueOrderByIdNutrienteAsc();

    List<Nutriente> findByNombreContainingIgnoreCaseAndActivoTrue(String palabraClave);

    List<Nutriente> findByCategoriaAndActivoTrueOrderByIdNutrienteAsc(CategoriaNutriente categoria);
}