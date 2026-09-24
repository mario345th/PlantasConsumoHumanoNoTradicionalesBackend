package sv.edu.ues.fmp.flora.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UsuarioCambioClaveRequest(
        @NotBlank(message = "La clave actual es obligatoria")
        String claveActual,

        @NotBlank(message = "La clave nueva es obligatoria")
        @Size(min = 8, max = 72, message = "La clave debe tener entre 8 y 72 caracteres")
        @Pattern(
                regexp = "^(?=(?:.*[A-Z]){3,})(?=(?:.*[a-z]){2,})(?=(?:.*[0-9]){2,}).+$",
                message = "La clave debe tener al menos 3 mayúsculas, 2 minúsculas y 2 números")
        String claveNueva
) {
}
