package sv.edu.ues.fmp.flora.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos que salen de un nombre comun.
 * <p>
 * No incluye datos de la especie: un nombre comun siempre se consulta en el
 * contexto de una especie que el cliente ya conoce, asi que repetirla seria
 * ruido en cada elemento del listado.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NombreComunResponse {

    private Long idNombreComun;
    private String nombre;
    private String region;
    private Boolean esPrincipal;
    private Boolean activo;
}
