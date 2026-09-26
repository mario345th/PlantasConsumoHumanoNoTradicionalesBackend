package sv.edu.ues.fmp.flora.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sv.edu.ues.fmp.flora.entity.Beneficio;

import java.util.Optional;

public interface BeneficioRepository extends JpaRepository<Beneficio, Long> {

    boolean existsByNombreIgnoreCase(String nombre);

    Optional<Beneficio> findByNombreIgnoreCase(String nombre);
}