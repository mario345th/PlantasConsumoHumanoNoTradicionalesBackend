package sv.edu.ues.fmp.flora.entity;

import jakarta.persistence.*;
import lombok.*;
import sv.edu.ues.fmp.flora.entity.enums.TipoConsumo;

/** Preparacion de una parte comestible. Imagenes y videos pertenecen a otros modulos. */
@Entity
@Table(name = "preparacion_consumo", uniqueConstraints =
        @UniqueConstraint(name = "uk_preparacion_parte_nombre", columnNames = {"id_especie_parte", "nombre"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreparacionConsumo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_preparacion", nullable = false, updatable = false)
    private Long idPreparacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_especie_parte", nullable = false)
    private EspecieParteComestible especieParteComestible;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "descripcion", columnDefinition = "text")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_consumo", nullable = false, length = 15)
    private TipoConsumo tipoConsumo;

    @Builder.Default
    @Column(name = "requiere_coccion", nullable = false)
    private Boolean requiereCoccion = false;

    @Column(name = "ingredientes", columnDefinition = "text")
    private String ingredientes;

    @Column(name = "procedimiento", nullable = false, columnDefinition = "text")
    private String procedimiento;

    @Column(name = "tiempo_preparacion_min")
    private Integer tiempoPreparacionMin;

    @Column(name = "numero_porciones")
    private Integer numeroPorciones;

    @Column(name = "advertencias", columnDefinition = "text")
    private String advertencias;

    @Column(name = "fuente_tradicional", length = 300)
    private String fuenteTradicional;

    @Builder.Default
    @Column(name = "orden_presentacion", nullable = false)
    private Short ordenPresentacion = 1;

    @Builder.Default
    @Column(name = "activa", nullable = false)
    private Boolean activa = true;
}
