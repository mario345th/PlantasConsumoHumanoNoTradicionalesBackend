package sv.edu.ues.fmp.flora.mapper;

import org.springframework.stereotype.Component;
import sv.edu.ues.fmp.flora.dto.request.PreparacionConsumoRequest;
import sv.edu.ues.fmp.flora.dto.response.PreparacionConsumoResponse;
import sv.edu.ues.fmp.flora.entity.EspecieParteComestible;
import sv.edu.ues.fmp.flora.entity.PreparacionConsumo;

@Component
public class PreparacionConsumoMapper {
    public PreparacionConsumo toEntity(PreparacionConsumoRequest request, EspecieParteComestible parte) {
        PreparacionConsumo entidad = PreparacionConsumo.builder().build();
        updateEntity(entidad, request, parte);
        return entidad;
    }

    public void updateEntity(PreparacionConsumo entidad, PreparacionConsumoRequest request,
                             EspecieParteComestible parte) {
        entidad.setEspecieParteComestible(parte);
        entidad.setNombre(request.getNombre());
        entidad.setDescripcion(limpiar(request.getDescripcion()));
        entidad.setTipoConsumo(request.getTipoConsumo());
        entidad.setIngredientes(limpiar(request.getIngredientes()));
        entidad.setProcedimiento(limpiar(request.getProcedimiento()));
        entidad.setTiempoPreparacionMin(request.getTiempoPreparacionMin());
        entidad.setNumeroPorciones(request.getNumeroPorciones());
        entidad.setAdvertencias(limpiar(request.getAdvertencias()));
        entidad.setFuenteTradicional(limpiar(request.getFuenteTradicional()));
        if (request.getRequiereCoccion() != null) {
            entidad.setRequiereCoccion(request.getRequiereCoccion());
        }
        if (request.getOrdenPresentacion() != null) {
            entidad.setOrdenPresentacion(request.getOrdenPresentacion().shortValue());
        }
    }

    public PreparacionConsumoResponse toResponse(PreparacionConsumo entidad) {
        return PreparacionConsumoResponse.builder()
                .idPreparacion(entidad.getIdPreparacion())
                .idEspecieParte(entidad.getEspecieParteComestible().getIdEspecieParte())
                .nombre(entidad.getNombre())
                .descripcion(entidad.getDescripcion())
                .tipoConsumo(entidad.getTipoConsumo())
                .requiereCoccion(entidad.getRequiereCoccion())
                .ingredientes(entidad.getIngredientes())
                .procedimiento(entidad.getProcedimiento())
                .tiempoPreparacionMin(entidad.getTiempoPreparacionMin())
                .numeroPorciones(entidad.getNumeroPorciones())
                .advertencias(entidad.getAdvertencias())
                .fuenteTradicional(entidad.getFuenteTradicional())
                .ordenPresentacion(entidad.getOrdenPresentacion())
                .activa(entidad.getActiva())
                .build();
    }

    // Conserva saltos de linea e indentacion interna de recetas.
    private String limpiar(String texto) {
        if (texto == null) return null;
        String limpio = texto.strip();
        return limpio.isEmpty() ? null : limpio;
    }
}
