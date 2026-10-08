package sv.edu.ues.fmp.flora.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PreparacionConsumoUpdateRequest extends PreparacionConsumoRequest {
    @Schema(description = "Omitido o null conserva el estado. true reactiva; false desactiva.")
    private Boolean activa;
}
