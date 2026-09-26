package sv.edu.ues.fmp.flora.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sv.edu.ues.fmp.flora.entity.enums.CategoriaNutriente;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NutrienteResponse {
    private Long idNutriente;
    private String nombre;
    private CategoriaNutriente categoria;
    private String descripcion;
    private Boolean activo;

}
