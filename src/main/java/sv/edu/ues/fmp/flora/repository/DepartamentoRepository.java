package sv.edu.ues.fmp.flora.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sv.edu.ues.fmp.flora.entity.Departamento;

import java.util.Optional;

@Repository
public interface DepartamentoRepository extends JpaRepository<Departamento, Long> {

    boolean existsByNombreIgnoreCase(String nombre);

    Optional<Departamento> findByNombreIgnoreCase(String nombre);
}
