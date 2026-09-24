package sv.edu.ues.fmp.flora.entity.enums;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RolPermisoId implements Serializable {

    @Column(name = "id_rol", nullable = false)
    private Long idRol;

    @Column(name = "id_permiso", nullable = false)
    private Long idPermiso;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RolPermisoId that = (RolPermisoId) o;
        return Objects.equals(idRol, that.idRol)
                && Objects.equals(idPermiso, that.idPermiso);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idRol, idPermiso);
    }
}
