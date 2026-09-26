package sv.edu.ues.fmp.flora.mapper;

import org.springframework.stereotype.Component;
import sv.edu.ues.fmp.flora.dto.request.NutrienteRequest;
import sv.edu.ues.fmp.flora.dto.response.NutrienteResponse;
import sv.edu.ues.fmp.flora.entity.Nutriente;
import sv.edu.ues.fmp.flora.entity.enums.CategoriaNutriente;

@Component
public class NutrienteMapper {

    public Nutriente toEntity(NutrienteRequest request) {
        return Nutriente.builder()
                .nombre(request.getNombre() != null ? request.getNombre().trim() : null)
                .categoria(CategoriaNutriente.valueOf(request.getCategoria().trim()))
                .descripcion(request.getDescripcion() != null && !request.getDescripcion().trim().isEmpty()
                        ? request.getDescripcion().trim()
                        : null)
                .build();
    }

    public void updateEntity(Nutriente entity, NutrienteRequest request) {
        entity.setNombre(request.getNombre() != null ? request.getNombre().trim() : null);
        entity.setCategoria(CategoriaNutriente.valueOf(request.getCategoria().trim()));
        entity.setDescripcion(request.getDescripcion() != null && !request.getDescripcion().trim().isEmpty()
                ? request.getDescripcion().trim()
                : null);
    }

    public NutrienteResponse toResponse(Nutriente entity) {
        return NutrienteResponse.builder()
                .idNutriente(entity.getIdNutriente())
                .nombre(entity.getNombre())
                .categoria(entity.getCategoria())
                .descripcion(entity.getDescripcion())
                .activo(entity.getActivo())
                .build();
    }
}