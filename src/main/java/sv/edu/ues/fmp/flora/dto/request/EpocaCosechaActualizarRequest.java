package sv.edu.ues.fmp.flora.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EpocaCosechaActualizarRequest {

    @NotNull(message = "El mes de inicio es obligatorio")
    @Min(value = 1, message = "El mes de inicio debe estar entre 1 y 12")
    @Max(value = 12, message = "El mes de inicio debe estar entre 1 y 12")
    private Short mesInicio;

    @NotNull(message = "El mes de fin es obligatorio")
    @Min(value = 1, message = "El mes de fin debe estar entre 1 y 12")
    @Max(value = 12, message = "El mes de fin debe estar entre 1 y 12")
    private Short mesFin;

    private String observacion;

    @Schema(hidden = true)
    @Null(message = "La parte comestible no se puede cambiar al actualizar la época")
    private Long idEspecieParte;
}