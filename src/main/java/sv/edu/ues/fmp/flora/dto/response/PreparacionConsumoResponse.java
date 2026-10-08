package sv.edu.ues.fmp.flora.dto.response;

import lombok.*;
import sv.edu.ues.fmp.flora.entity.enums.TipoConsumo;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreparacionConsumoResponse {
    private Long idPreparacion;
    private Long idEspecieParte;
    private String nombre;
    private String descripcion;
    private TipoConsumo tipoConsumo;
    private Boolean requiereCoccion;
    private String ingredientes;
    private String procedimiento;
    private Integer tiempoPreparacionMin;
    private Integer numeroPorciones;
    private String advertencias;
    private String fuenteTradicional;
    private Short ordenPresentacion;
    private Boolean activa;
}
