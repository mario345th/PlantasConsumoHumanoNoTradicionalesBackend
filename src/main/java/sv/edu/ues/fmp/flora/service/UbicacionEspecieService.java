package sv.edu.ues.fmp.flora.service;

import sv.edu.ues.fmp.flora.dto.request.UbicacionEspecieRequest;
import sv.edu.ues.fmp.flora.dto.response.UbicacionEspecieResponse;

import java.util.List;

public interface UbicacionEspecieService {

    UbicacionEspecieResponse crear(UbicacionEspecieRequest request);

    List<UbicacionEspecieResponse> obtenerUbicacionesPorEspecie(Long idEspecie);

    UbicacionEspecieResponse actualizarUbicacion(Long idUbicacion, UbicacionEspecieRequest request);

    void desactivarUbicacion(Long idUbicacion);

    /**
     * Revierte la baja logica de una ubicacion.
     * <p>
     * No es un simple {@code activa = true}. Exige que la especie siga activa,
     * igual que crear y actualizar. Y respeta el indice parcial
     * {@code uk_ubicacion_especie_coords_activa} (V11), que solo admite una
     * fila activa por (especie, latitud, longitud): mientras esta ubicacion
     * estuvo de baja otra pudo ocupar esas coordenadas. Cualquiera de los dos
     * choques se rechaza con 409 antes del UPDATE.
     * <p>
     * Localiza la fila por su id y no revalida el Request (caja de El
     * Salvador, fecha de observacion, etc.): el dato ya existia en la base.
     */
    UbicacionEspecieResponse reactivarUbicacion(Long idUbicacion);
}