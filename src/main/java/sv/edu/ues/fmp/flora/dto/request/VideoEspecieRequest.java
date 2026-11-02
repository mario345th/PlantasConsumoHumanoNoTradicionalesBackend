package sv.edu.ues.fmp.flora.dto.request;

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

/** Datos editables para crear o actualizar un video de una especie. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoEspecieRequest {

    /** Mismas reglas que VideoPreparacionRequest; ver los hallazgos VP-H02, VP-H04 y VP-H06. */
    private static final String PATRON_SIN_ETIQUETAS = "^[^<>]*$";

    private static final String PATRON_URL =
            "^https?://([\\w-]+\\.)+[a-zA-Z]{2,}(:\\d{1,5})?(/[^\\s]*)?$";

    private static final int MAX_DURACION_SEGUNDOS = 14400;

    @NotBlank(message = "El título es obligatorio")
    @Size(max = 200, message = "El título no puede exceder 200 caracteres")
    @Pattern(regexp = PATRON_SIN_ETIQUETAS, message = "El título no puede contener los caracteres < ni >")
    private String titulo;

    @Pattern(regexp = PATRON_SIN_ETIQUETAS, message = "La descripción no puede contener los caracteres < ni >")
    private String descripcion;

    @NotBlank(message = "La URL del video es obligatoria")
    @Size(max = 1000, message = "La URL del video no puede exceder 1000 caracteres")
    @Pattern(regexp = PATRON_URL, message = "La URL del video debe ser válida e incluir un dominio con http:// o https://")
    private String urlVideo;

    @Positive(message = "La duración debe ser mayor que cero")
    @Max(value = MAX_DURACION_SEGUNDOS, message = "La duración no puede exceder 14400 segundos (4 horas)")
    private Integer duracionSegundos;

    @Size(max = 250, message = "Los créditos no pueden exceder 250 caracteres")
    @Pattern(regexp = PATRON_SIN_ETIQUETAS, message = "Los créditos no pueden contener los caracteres < ni >")
    private String creditos;

    private Long idEspecieParte;
}
