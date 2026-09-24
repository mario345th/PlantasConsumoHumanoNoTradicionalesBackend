package sv.edu.ues.fmp.flora.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos que entran al crear o actualizar un nombre comun.
 * <p>
 * No lleva {@code idNombreComun} (lo asigna la base), ni {@code idEspecie}
 * (la especie viaja en la ruta anidada y el servicio la resuelve), ni
 * {@code activo} (la baja y el alta logicas tienen endpoints propios).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NombreComunRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
    private String nombre;

    @Size(max = 150, message = "La región no puede exceder 150 caracteres")
    private String region;

    /**
     * Obligatorio y sin valor por defecto: quien crea el nombre tiene que
     * decidir explicitamente si es el principal de la especie.
     */
    @NotNull(message = "Debe indicarse si el nombre es el principal de la especie")
    private Boolean esPrincipal;
}
