package sv.edu.ues.fmp.flora.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import sv.edu.ues.fmp.flora.entity.Permiso;

@Repository
public interface PermisoRepository extends JpaRepository<Permiso, Long> {

    boolean existsByCodigoIgnoreCase(String codigo);

    Optional<Permiso> findByCodigoIgnoreCase(String codigo);

    List<Permiso> findByActivoTrue();
}
