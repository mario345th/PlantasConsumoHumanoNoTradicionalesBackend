package sv.edu.ues.fmp.flora.mapper;

import org.springframework.stereotype.Component;

import sv.edu.ues.fmp.flora.dto.request.NombreComunRequest;
import sv.edu.ues.fmp.flora.dto.response.NombreComunResponse;
import sv.edu.ues.fmp.flora.entity.Especie;
import sv.edu.ues.fmp.flora.entity.NombreComun;

/**
 * Convierte entre los DTOs de nombre comun y la entidad.
 * No consulta repositorios: la especie llega ya resuelta por el servicio.
 */
@Component
public class NombreComunMapper {

    /**
     * Normaliza la region: cadenas nulas, vacias o de solo espacios se
     * convierten en null, de modo que el servicio vea el mismo valor que el
     * indice unico {@code uk_nombre_comun_especie_lower}, que aplica
     * {@code COALESCE(LOWER(region), '')} y por tanto considera iguales el NULL
     * y la cadena vacia. Sin esta normalizacion, una peticion con
     * {@code region=""} chocaria contra un registro existente con
     * {@code region=null} y se recibiria el 409 generico de
     * {@code DataIntegrityViolationException} en lugar del mensaje especifico
     * del servicio.
     * <p>
     * El {@code trim()} es intencional: guardar {@code "  El Salvador  "} con
     * los espacios de los extremos es un error de datos que conviene atajar
     * aqui, y ademas haria que ese valor no coincidiera con el
     * {@code "El Salvador"} ya almacenado.
     * <p>
     * Es publico, y no privado, porque el servicio tiene que normalizar con
     * esta misma regla los parametros de sus consultas de duplicados antes de
     * llegar al mapper: si validara con el valor crudo del request y solo se
     * normalizara al construir la entidad, la comprobacion y el INSERT usarian
     * valores distintos y el hueco seguiria abierto. La regla vive aqui, en un
     * unico sitio, para que no pueda divergir.
     */
    public String normalizarRegion(String region) {
        return (region == null || region.isBlank()) ? null : region.trim();
    }

    /** Recibe la Especie YA validada por el servicio. */
    public NombreComun toEntity(NombreComunRequest request, Especie especie) {
        return NombreComun.builder()
                .especie(especie)
                .nombre(request.getNombre())
                .region(normalizarRegion(request.getRegion()))
                .esPrincipal(request.getEsPrincipal())
                .build();
    }

    /**
     * Copia los campos editables sobre una entidad ya gestionada.
     * No toca {@code activo} (tiene endpoints propios) ni la especie: un
     * nombre comun no cambia de especie.
     */
    public void updateEntity(NombreComun entity, NombreComunRequest request) {
        entity.setNombre(request.getNombre());
        entity.setRegion(normalizarRegion(request.getRegion()));
        entity.setEsPrincipal(request.getEsPrincipal());
    }

    public NombreComunResponse toResponse(NombreComun entity) {
        return NombreComunResponse.builder()
                .idNombreComun(entity.getIdNombreComun())
                .nombre(entity.getNombre())
                .region(entity.getRegion())
                .esPrincipal(entity.getEsPrincipal())
                .activo(entity.getActivo())
                .build();
    }
}
