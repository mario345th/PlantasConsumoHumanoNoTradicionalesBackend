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
public class RolPermisoRequest {

    @NotNull(message = "El id del rol es obligatorio")
    @Positive(message = "El id del rol debe ser un valor positivo")
    private Long idRol;

    @NotNull(message = "El id del permiso es obligatorio")
    @Positive(message = "El id del permiso debe ser un valor positivo")
    private Long idPermiso;
}
