package sv.edu.ues.fmp.flora.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Datos para registrar o actualizar un video de una preparacion. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoPreparacionRequest {

    /**
     * Rechaza los campos de texto que traigan < o >, para que no se guarde
     * marcado HTML ni un <script> que el frontend pudiera ejecutar al
     * mostrarlo sin escapar (hallazgo VP-H04 del 3er informe de QA).
     */
    private static final String PATRON_SIN_ETIQUETAS = "^[^<>]*$";

    /**
     * Exige host con dominio y TLD, no solo el esquema: el patrón anterior
     * "^https?://\S+$" aceptaba "https://a" (hallazgo VP-H02). Es el mismo
     * patrón que ya usa ImagenPreparacionRequest.
     */
    private static final String PATRON_URL =
            "^https?://([\\w-]+\\.)+[a-zA-Z]{2,}(:\\d{1,5})?(/[^\\s]*)?$";

    /** 4 horas. Tope acordado a partir de la recomendación de QA (VP-H06). */
    private static final int MAX_DURACION_SEGUNDOS = 14400;

    @NotBlank(message = "El título es obligatorio")
    @Size(max = 200, message = "El título no puede exceder 200 caracteres")
    @Pattern(regexp = PATRON_SIN_ETIQUETAS, message = "El título no puede contener los caracteres < ni >")
    @Schema(description = "Título del video", example = "Elaboración paso a paso de la receta")
    private String titulo;

    @Pattern(regexp = PATRON_SIN_ETIQUETAS, message = "La descripción no puede contener los caracteres < ni >")
    @Schema(description = "Descripción opcional del contenido del video", example = "Explicación del tiempo de cocción y recomendaciones")
    private String descripcion;

    @NotBlank(message = "La URL del video es obligatoria")
    @Size(max = 1000, message = "La URL del video no puede exceder 1000 caracteres")
    @Pattern(regexp = PATRON_URL, message = "La URL del video debe ser válida e incluir un dominio con http:// o https://")
    @Schema(description = "URL accesible del video", example = "https://www.youtube.com/watch?v=ejemplo123")
    private String urlVideo;

    @Positive(message = "La duración debe ser mayor que cero")
    @Max(value = MAX_DURACION_SEGUNDOS, message = "La duración no puede exceder 14400 segundos (4 horas)")
    @Schema(description = "Duración total del video en segundos", example = "180")
    private Integer duracionSegundos;

    @Size(max = 250, message = "Los créditos no pueden exceder 250 caracteres")
    @Pattern(regexp = PATRON_SIN_ETIQUETAS, message = "Los créditos no pueden contener los caracteres < ni >")
    @Schema(description = "Autor o créditos del video", example = "Canal de Cocina Tradicional")
    private String creditos;
}

