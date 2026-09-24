package sv.edu.ues.fmp.flora.service;

import java.util.List;

import sv.edu.ues.fmp.flora.dto.request.NombreComunRequest;
import sv.edu.ues.fmp.flora.dto.response.NombreComunResponse;

/**
 * Contrato de negocio para los nombres comunes de una especie.
 * Trabaja solo con DTOs: la entidad no cruza hacia el controlador.
 * <p>
 * Los listados y la creacion reciben el id de la especie porque un nombre
 * comun no existe fuera de ella; el resto de operaciones se identifican con el
 * id del propio nombre, que ya es unico en todo el catalogo.
 */
public interface NombreComunService {

    /** Todos los nombres de la especie, activos e inactivos. */
    List<NombreComunResponse> listarPorEspecie(Long idEspecie);

    List<NombreComunResponse> listarActivosPorEspecie(Long idEspecie);

    NombreComunResponse obtenerPorId(Long id);

    NombreComunResponse crear(Long idEspecie, NombreComunRequest request);

    NombreComunResponse actualizar(Long id, NombreComunRequest request);

    /** Promueve un nombre a principal sin tener que reenviar el recurso completo. */
    NombreComunResponse marcarComoPrincipal(Long id);

    /** Baja logica. Puede fallar si dejaria sin principal a una especie con otros nombres. */
    void desactivar(Long id);

    NombreComunResponse activar(Long id);
}
