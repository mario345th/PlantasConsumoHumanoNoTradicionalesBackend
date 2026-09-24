package sv.edu.ues.fmp.flora.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UsuarioLoginRequest(
        @NotBlank(message = "El usuario es obligatorio")
        String usuario,

        @NotBlank(message = "La clave es obligatoria")
        String clave
) {
    public UsuarioLoginRequest {
        usuario = usuario == null ? null : usuario.trim();
    }
}
