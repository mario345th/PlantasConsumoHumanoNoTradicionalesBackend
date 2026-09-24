package sv.edu.ues.fmp.flora.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sv.edu.ues.fmp.flora.entity.enums.TipoBeneficio;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BeneficioResponse {

    private Long idBeneficio;

    private String nombre;

    private TipoBeneficio tipoBeneficio;

    private String descripcion;

    private Boolean activo;
}