package sv.edu.ues.fmp.flora.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sv.edu.ues.fmp.flora.entity.Beneficio;

@Repository
public interface BeneficioRepository
        extends JpaRepository<Beneficio, Long> {
}