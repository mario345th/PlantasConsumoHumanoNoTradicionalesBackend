package sv.edu.ues.fmp.flora.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import sv.edu.ues.fmp.flora.entity.Permiso;
import sv.edu.ues.fmp.flora.entity.RolPermiso;
import sv.edu.ues.fmp.flora.entity.enums.RolPermisoId;

@Repository
public interface RolPermisoRepository extends JpaRepository<RolPermiso, RolPermisoId> {

    @Query("SELECT rp.permiso FROM RolPermiso rp WHERE rp.rol.idRol = :idRol")
    List<Permiso> findPermisosByRolId(@Param("idRol") Long idRol);
}
