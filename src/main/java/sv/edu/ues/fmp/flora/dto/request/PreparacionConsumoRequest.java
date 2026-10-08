package sv.edu.ues.fmp.flora.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sv.edu.ues.fmp.flora.entity.enums.TipoConsumo;

@Getter
@Setter
@NoArgsConstructor
public class PreparacionConsumoRequest {
    @NotNull(message = "La parte comestible es obligatoria")
    @Positive(message = "El id de la parte comestible debe ser positivo")
    private Long idEspecieParte;

    @NotBlank(message = "El nombre es obligatorio y no puede estar vacío")
    @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
    @Pattern(regexp = "(?s).*\\p{L}.*", message = "El nombre debe contener al menos una letra")
    @Schema(description = "Debe contener al menos una letra. Puede incluir números y signos.")
    private String nombre;

    @NotBlank(message = "La descripción es obligatoria y no puede estar vacía")
    @Pattern(regexp = "(?s).*\\p{L}.*", message = "La descripción debe contener al menos una letra")
    @Schema(description = "Obligatoria y con al menos una letra. Permite números, signos y saltos de línea.")
    private String descripcion;

    @NotNull(message = "El tipo de consumo es obligatorio")
    private TipoConsumo tipoConsumo;

    @Schema(description = "Al crear, omitido o null equivale a false. Al actualizar conserva el valor anterior.")
    private Boolean requiereCoccion;

    private String ingredientes;

    @NotBlank(message = "El procedimiento es obligatorio y no puede estar vacío")
    @Pattern(regexp = "(?s).*\\p{L}.*", message = "El procedimiento debe contener al menos una letra")
    @Schema(description = "Obligatorio y con al menos una letra. Permite pasos numerados, signos y saltos de línea.")
    private String procedimiento;

    @Positive(message = "El tiempo de preparación debe ser mayor que cero")
    private Integer tiempoPreparacionMin;

    @Positive(message = "El número de porciones debe ser mayor que cero")
    private Integer numeroPorciones;

    private String advertencias;

    @Size(max = 300, message = "La fuente tradicional no puede superar 300 caracteres")
    private String fuenteTradicional;

    @Min(value = 1, message = "El orden de presentación debe ser mayor que cero")
    @Max(value = 32767, message = "El orden de presentación no puede superar 32767")
    @Schema(description = "Entre 1 y 32767. Omitido o null: 1 al crear; conserva el valor al actualizar.")
    private Integer ordenPresentacion;

    // Normalizar antes de validar y buscar duplicados; no altera textos de varios renglones.
    public void setNombre(String nombre) {
        this.nombre = nombre == null ? null : nombre.strip().replaceAll("(?U)\\s+", " ");
    }
}
