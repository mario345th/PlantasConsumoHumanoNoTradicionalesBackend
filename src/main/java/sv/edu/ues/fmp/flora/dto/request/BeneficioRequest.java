package sv.edu.ues.fmp.flora.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sv.edu.ues.fmp.flora.entity.enums.TipoBeneficio;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BeneficioRequest {

    @NotBlank(message = "El nombre del beneficio es obligatorio")
    @Size(max = 150, message = "El nombre del beneficio no puede superar los 150 caracteres")
    private String nombre;

    @NotNull(message = "El tipo de beneficio es obligatorio")
    private TipoBeneficio tipoBeneficio;

    @NotBlank(message = "La descripción es obligatoria")
    private String descripcion;

    @Builder.Default
    @JsonSetter(nulls = Nulls.FAIL)
    private Boolean activo = true;
}