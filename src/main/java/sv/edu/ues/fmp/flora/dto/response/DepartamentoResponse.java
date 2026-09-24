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
public class DepartamentoResponse {
    /** Identificador generado por la base de datos. */
    private Long idDepartamento;
    /** Nombre público del departamento. */
    private String nombre;
    /** Indica si el registro permanece habilitado tras una baja lógica. */
    private Boolean activo;
}
