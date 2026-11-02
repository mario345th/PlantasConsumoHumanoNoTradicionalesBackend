package sv.edu.ues.fmp.flora.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;

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

    /** Tope de {@link #tiempoPreparacionMin}, en minutos. */
    private static final int MAX_TIEMPO_PREPARACION_MIN = 1440;

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

    /**
     * 24 horas. QA acepto un tiempo de 999999999 minutos (mas de 1900 anios)
     * porque solo se validaba que fuera positivo. El tope cubre con holgura
     * los remojos y fermentaciones largas de las preparaciones tradicionales;
     * si el equipo necesita mas, se cambia aqui.
     */
    @Positive(message = "El tiempo de preparación debe ser mayor que cero")
    @Max(value = MAX_TIEMPO_PREPARACION_MIN,
            message = "El tiempo de preparación no puede exceder 1440 minutos (24 horas)")
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

    /**
     * Un consumo crudo no puede requerir coccion.
     * <p>
     * QA acepto {@code tipoConsumo: CRUDO} junto a {@code requiereCoccion:
     * true}, que se contradicen entre si. De las siete opciones del enum esta
     * es la unica combinacion imposible por definicion: COCIDO, ASADO, FRITO e
     * INFUSION si pueden llevar {@code requiereCoccion} en false cuando el
     * ingrediente llega ya cocido de un paso anterior, y BEBIDA y OTRO admiten
     * las dos. Endurecer tambien ese lado queda a decision del equipo.
     * <p>
     * Va como {@code @AssertTrue} porque la regla cruza dos campos, con
     * {@code @JsonIgnore} para que la clave no aparezca como campo del cuerpo,
     * igual que {@code ImagenEspecieRequest.isUrlValida()}.
     */
    @Schema(hidden = true)
    @JsonIgnore
    @AssertTrue(message = "Un consumo crudo no puede requerir cocción")
    public boolean isCoccionCoherenteConElTipo() {
        return tipoConsumo != TipoConsumo.CRUDO || !Boolean.TRUE.equals(requiereCoccion);
    }
}
