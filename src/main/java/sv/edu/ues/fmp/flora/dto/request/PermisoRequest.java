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
public class PermisoRequest {

    @NotBlank(message = "El código es obligatorio")
    @Size(max = 80, message = "El código no puede exceder 80 caracteres")
    @Pattern(
            regexp = "^[A-Z0-9_]+$",
            message = "El código solo puede contener letras mayúsculas, números y guiones bajos")
    private String codigo;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 120, message = "El nombre no puede exceder 120 caracteres")
    private String nombre;

    @Size(max = 250, message = "La descripción no puede exceder 250 caracteres")
    private String descripcion;

    public String getCodigo() {
        return codigo == null ? null : codigo.trim();
    }

    public String getNombre() {
        return nombre == null ? null : nombre.trim();
    }
}
