package sv.edu.ues.fmp.flora.dto.request;

import jakarta.validation.constraints.NotNull;
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
public class EspecieHabitatRequest {

    @NotNull(message = "La especie es obligatoria")
    private Long idEspecie;

    @NotNull(message = "El hábitat es obligatorio")
    private Long idHabitat;

    /** Texto opcional, sin limite adicional al definido por PostgreSQL. */
    private String observacion;
}
