package sv.edu.ues.fmp.flora.dto.response;

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
public class EspecieHabitatResponse {
    private Long idEspecie;
    private Long idHabitat;
    private String nombreEspecie;
    private String nombreHabitat;
    private String observacion;
}
