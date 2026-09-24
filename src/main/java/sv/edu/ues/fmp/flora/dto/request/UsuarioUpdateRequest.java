package sv.edu.ues.fmp.flora.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UsuarioUpdateRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
        String nombres,

        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 100, message = "El apellido no puede exceder 100 caracteres")
        String apellidos,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 150, message = "El correo no puede exceder 150 caracteres")
        String correo,

        @NotBlank(message = "El nombre de usuario es obligatorio")
        @Size(min = 4, max = 50, message = "El nombre de usuario debe tener entre 4 y 50 caracteres")
        @Pattern(
                regexp = "^[a-zA-Z0-9._-]+$",
                message = "El nombre de usuario solo puede contener letras, números, puntos, guiones y guiones bajos")
        String nombreUsuario
) {
    public UsuarioUpdateRequest {
        nombres = nombres == null ? null : nombres.trim();
        apellidos = apellidos == null ? null : apellidos.trim();
        correo = correo == null ? null : correo.trim();
        nombreUsuario = nombreUsuario == null ? null : nombreUsuario.trim();
    }
}
