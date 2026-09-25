package sv.edu.ues.fmp.flora.entity.enums;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

/** Clave compuesta de especie_habitat, siguiendo el patron de RolPermisoId. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EspecieHabitatId implements Serializable {

    @Column(name = "id_especie", nullable = false)
    private Long idEspecie;

    @Column(name = "id_habitat", nullable = false)
    private Long idHabitat;

    /**
     * Compara ambos IDs para determinar si dos claves representan la misma relación.
     * El orden importa: especie 1/hábitat 2 es distinto de especie 2/hábitat 1.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EspecieHabitatId that = (EspecieHabitatId) o;
        return Objects.equals(idEspecie, that.idEspecie)
                && Objects.equals(idHabitat, that.idHabitat);
    }

    /**
     * Calcula el hash usando los mismos dos campos que equals, manteniendo la
     * coherencia de la identidad en colecciones y en el contexto de persistencia.
     */
    @Override
    public int hashCode() {
        return Objects.hash(idEspecie, idHabitat);
    }
}
