package sv.edu.ues.fmp.flora.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "epoca_cosecha",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_epoca_parte_periodo",
                columnNames = {"id_especie_parte", "mes_inicio", "mes_fin"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EpocaCosecha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_epoca_cosecha", nullable = false, updatable = false)
    private Long idEpocaCosecha;

    @NotNull(message = "La parte comestible de la especie es obligatoria")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_especie_parte", nullable = false)
    private EspecieParteComestible especieParteComestible;

    @NotNull(message = "El mes de inicio es obligatorio")
    @Min(value = 1, message = "El mes de inicio debe estar entre 1 y 12")
    @Max(value = 12, message = "El mes de inicio debe estar entre 1 y 12")
    @Column(name = "mes_inicio", nullable = false)
    private Short mesInicio;

    @NotNull(message = "El mes de fin es obligatorio")
    @Min(value = 1, message = "El mes de fin debe estar entre 1 y 12")
    @Max(value = 12, message = "El mes de fin debe estar entre 1 y 12")
    @Column(name = "mes_fin", nullable = false)
    private Short mesFin;

    @Column(name = "observacion", columnDefinition = "text")
    private String observacion;

    @NotNull(message = "El estado activo es obligatorio")
    @Builder.Default
    @Column(name = "activa", nullable = false)
    private Boolean activa = true;
}