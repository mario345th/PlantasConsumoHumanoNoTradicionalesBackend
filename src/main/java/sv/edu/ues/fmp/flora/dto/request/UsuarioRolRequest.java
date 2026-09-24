package sv.edu.ues.fmp.flora.dto.request;

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
public class UsuarioRolRequest {

    @NotNull(message = "El id del usuario es obligatorio")
    @Positive(message = "El id del usuario debe ser un valor positivo")
    private Long idUsuario;

    @NotNull(message = "El id del rol es obligatorio")
    @Positive(message = "El id del rol debe ser un valor positivo")
    private Long idRol;
}
