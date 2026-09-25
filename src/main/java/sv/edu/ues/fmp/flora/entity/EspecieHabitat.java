package sv.edu.ues.fmp.flora.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import org.springframework.data.domain.Persistable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sv.edu.ues.fmp.flora.entity.enums.EspecieHabitatId;

/** Relacion sin identidad artificial ni cascadas hacia sus entidades padre. */
@Entity
@Table(name = "especie_habitat")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EspecieHabitat implements Persistable<EspecieHabitatId> {

    // Una PK asignada no implica que la fila exista. Forzar persist al crear evita
    // que save haga merge y sobrescriba una relacion creada simultaneamente.
    @Transient
    @Builder.Default
    @Getter(lombok.AccessLevel.NONE)
    @Setter(lombok.AccessLevel.NONE)
    private boolean nueva = true;

    /**
     * Indica a Spring Data si debe insertar la relación aunque ya tenga una clave
     * asignada. Evita tratar una entidad nueva como una actualización mediante merge.
     */
    @Override
    public boolean isNew() {
        return nueva;
    }

    /**
     * Callback de JPA que marca la entidad como existente después de cargarla
     * o insertarla, para que las siguientes operaciones reconozcan su estado.
     */
    @PostLoad
    @PostPersist
    private void marcarPersistida() {
        nueva = false;
    }

    @EmbeddedId
    private EspecieHabitatId id;

    @MapsId("idEspecie")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_especie", nullable = false)
    private Especie especie;

    @MapsId("idHabitat")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_habitat", nullable = false)
    private Habitat habitat;

    @Column(name = "observacion", columnDefinition = "text")
    private String observacion;
}
