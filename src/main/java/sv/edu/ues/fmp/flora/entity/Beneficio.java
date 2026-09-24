package sv.edu.ues.fmp.flora.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sv.edu.ues.fmp.flora.entity.enums.TipoBeneficio;

/**
 * Beneficio asociado a una especie vegetal.
 * Corresponde a la tabla {@code beneficio}.
 * La columna {@code tipo_beneficio} se persiste como texto
 * (EnumType.STRING) sobre un varchar(20), protegido por un CHECK constraint.
 */
@Entity
@Table(name = "beneficio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Beneficio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_beneficio", nullable = false, updatable = false)
    private Long idBeneficio;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_beneficio", nullable = false, length = 20)
    private TipoBeneficio tipoBeneficio;

    @Column(name = "descripcion", columnDefinition = "text")
    private String descripcion;

    @Builder.Default
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;
}