package sv.edu.ues.fmp.flora.mapper;

import org.springframework.stereotype.Component;

import sv.edu.ues.fmp.flora.dto.request.EpocaCosechaActualizarRequest;
import sv.edu.ues.fmp.flora.dto.request.EpocaCosechaRequest;
import sv.edu.ues.fmp.flora.dto.response.EpocaCosechaResponse;
import sv.edu.ues.fmp.flora.entity.EpocaCosecha;
import sv.edu.ues.fmp.flora.entity.EspecieParteComestible;

@Component
public class EpocaCosechaMapper {

    public EpocaCosecha toEntity(
            EpocaCosechaRequest request,
            EspecieParteComestible parteComestible
    ) {
        return EpocaCosecha.builder()
                .especieParteComestible(parteComestible)
                .mesInicio(request.getMesInicio())
                .mesFin(request.getMesFin())
                .observacion(request.getObservacion())
                .build();
    }

    public void updateEntity(
            EpocaCosecha entidad,
            EpocaCosechaActualizarRequest request
    ) {
        entidad.setMesInicio(request.getMesInicio());
        entidad.setMesFin(request.getMesFin());
        entidad.setObservacion(request.getObservacion());
    }

    public EpocaCosechaResponse toResponse(EpocaCosecha entidad) {
        return EpocaCosechaResponse.builder()
                .idEpocaCosecha(entidad.getIdEpocaCosecha())
                .idEspecieParte(
                        entidad.getEspecieParteComestible().getIdEspecieParte()
                )
                .mesInicio(entidad.getMesInicio())
                .mesFin(entidad.getMesFin())
                .observacion(entidad.getObservacion())
                .activa(entidad.getActiva())
                .build();
    }
}