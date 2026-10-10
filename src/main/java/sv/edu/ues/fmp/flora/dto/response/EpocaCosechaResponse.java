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
public class EpocaCosechaResponse {

    private Long idEpocaCosecha;
    private Long idEspecieParte;
    private Short mesInicio;
    private Short mesFin;
    private String observacion;
    private Boolean activa;
}