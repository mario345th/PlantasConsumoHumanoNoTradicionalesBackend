package sv.edu.ues.fmp.flora.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EpocaCosechaRequest {

    @NotNull(message = "La parte comestible de la especie es obligatoria")
    @Positive(message = "El id de la parte comestible debe ser positivo")
    private Long idEspecieParte;

    @NotNull(message = "El mes de inicio es obligatorio")
    @Min(value = 1, message = "El mes de inicio debe estar entre 1 y 12")
    @Max(value = 12, message = "El mes de inicio debe estar entre 1 y 12")
    private Short mesInicio;

    @NotNull(message = "El mes de fin es obligatorio")
    @Min(value = 1, message = "El mes de fin debe estar entre 1 y 12")
    @Max(value = 12, message = "El mes de fin debe estar entre 1 y 12")
    private Short mesFin;

    private String observacion;
}