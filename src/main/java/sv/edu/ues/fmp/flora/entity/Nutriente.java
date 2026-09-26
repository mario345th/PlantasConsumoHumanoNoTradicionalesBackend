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
import sv.edu.ues.fmp.flora.entity.enums.CategoriaNutriente;

/**
 * Nutriente aportado por una especie. Corresponde a la tabla {@code nutriente}.
 * La columna {@code nombre} tiene restriccion UNIQUE y {@code categoria} se persiste
 * como texto (EnumType.STRING) sobre un varchar(20) protegido por CHECK constraint.
 */
@Entity
@Table(name = "nutriente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Nutriente {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_nutriente", nullable = false, updatable = false)
    private Long idNutriente;

    @Column(name = "nombre", nullable = false, unique = true, length = 120)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false, length = 20)
    private CategoriaNutriente categoria;

    @Column(name = "descripcion", columnDefinition = "text")
    private String descripcion;

    @Builder.Default
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;
}
