package sv.edu.ues.fmp.flora.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
public class NutrienteRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 120, message = "El nombre no puede exceder 120 caracteres")
    private String nombre;

    @NotBlank(message = "La categoría del nutriente es obligatoria y no puede estar vacía o con solo espacios")
    @Pattern(regexp = "^(MACRONUTRIENTE|VITAMINA|MINERAL|ENERGIA|FIBRA|OTRO)$",
            message = "La categoría ingresada no existe. Valores permitidos: MACRONUTRIENTE, VITAMINA, MINERAL, ENERGIA, FIBRA, OTRO")
    private String categoria;

    @NotBlank(message = "La descripción es obligatoria y no puede guardarse vacía o con solo espacios")
    private String descripcion;
}