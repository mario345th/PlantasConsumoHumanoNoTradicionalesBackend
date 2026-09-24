package sv.edu.ues.fmp.flora.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sv.edu.ues.fmp.flora.entity.enums.EstadoPublicacion;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ficha de especie tal como sale de la API.
 * <p>
 * Los responsables ({@code creadaPor}, {@code validadaPor},
 * {@code publicadaPor}) se exponen como nombre legible y no como id: el
 * consumidor necesita mostrar quien hizo que, no hacer otra peticion para
 * averiguarlo. Los dos ultimos son null mientras la especie no haya sido
 * validada o publicada.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EspecieResponse {

    private Long idEspecie;
    private String nombreCientifico;
    private String descripcion;
    private String origen;
    private String propiedades;
    private String importanciaCultural;
    private String advertencias;
    private EstadoPublicacion estadoPublicacion;
    private Boolean activa;

    private TaxonomiaResponse taxonomia;

    /**
     * Solo los nombres activos, con el principal a la cabeza. Nunca es null:
     * una especie sin nombres comunes, o con todos dados de baja, sale con
     * lista vacia.
     */
    private List<NombreComunResponse> nombresComunes;

    private String creadaPor;
    private String validadaPor;
    private String publicadaPor;

    private LocalDateTime fechaRegistro;
    private LocalDateTime fechaActualizacion;
    private LocalDateTime fechaValidacion;
    private LocalDateTime fechaPublicacion;
}
