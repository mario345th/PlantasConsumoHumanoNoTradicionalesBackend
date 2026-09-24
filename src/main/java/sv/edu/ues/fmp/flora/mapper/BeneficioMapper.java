package sv.edu.ues.fmp.flora.mapper;

import org.springframework.stereotype.Component;
import sv.edu.ues.fmp.flora.dto.request.BeneficioRequest;
import sv.edu.ues.fmp.flora.dto.response.BeneficioResponse;
import sv.edu.ues.fmp.flora.entity.Beneficio;

@Component
public class BeneficioMapper {

    public Beneficio toEntity(BeneficioRequest request) {

        return Beneficio.builder()
                .nombre(request.getNombre().trim())
                .tipoBeneficio(request.getTipoBeneficio())
                .descripcion(
                        request.getDescripcion() != null
                                ? request.getDescripcion().trim()
                                : null
                )
                .activo(
                        request.getActivo() != null
                                ? request.getActivo()
                                : true
                )
                .build();
    }

    public BeneficioResponse toResponse(Beneficio beneficio) {

        return BeneficioResponse.builder()
                .idBeneficio(beneficio.getIdBeneficio())
                .nombre(beneficio.getNombre())
                .tipoBeneficio(beneficio.getTipoBeneficio())
                .descripcion(beneficio.getDescripcion())
                .activo(beneficio.getActivo())
                .build();
    }

    public void updateEntity(
            Beneficio beneficio,
            BeneficioRequest request
    ) {
        beneficio.setNombre(request.getNombre().trim());
        beneficio.setTipoBeneficio(
                request.getTipoBeneficio()
        );
        beneficio.setDescripcion(
                request.getDescripcion() != null
                        ? request.getDescripcion().trim()
                        : null
        );
        if (request.getActivo() != null) {
            beneficio.setActivo(request.getActivo());
        }
    }
}