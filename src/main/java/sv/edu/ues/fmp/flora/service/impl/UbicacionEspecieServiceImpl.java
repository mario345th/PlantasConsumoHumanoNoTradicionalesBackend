package sv.edu.ues.fmp.flora.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sv.edu.ues.fmp.flora.dto.request.UbicacionEspecieRequest;
import sv.edu.ues.fmp.flora.dto.response.UbicacionEspecieResponse;
import sv.edu.ues.fmp.flora.entity.Distrito;
import sv.edu.ues.fmp.flora.entity.Especie;
import sv.edu.ues.fmp.flora.entity.UbicacionEspecie;
import sv.edu.ues.fmp.flora.exception.EstadoInvalidoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.repository.DistritoRepository;
import sv.edu.ues.fmp.flora.repository.EspecieRepository;
import sv.edu.ues.fmp.flora.repository.UbicacionEspecieRepository;
import sv.edu.ues.fmp.flora.service.UbicacionEspecieService;
import sv.edu.ues.fmp.flora.service.VisibilidadDeEspecie;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UbicacionEspecieServiceImpl implements UbicacionEspecieService {

    private final UbicacionEspecieRepository ubicacionEspecieRepository;
    private final EspecieRepository especieRepository;
    private final DistritoRepository distritoRepository;

    /**
     * Decide si el contenido de la especie padre se puede mostrar a quien
     * llama. Lo que no se puede ver se responde como lo que no existe.
     */
    private final VisibilidadDeEspecie visibilidadDeEspecie;

    @Override
    @Transactional
    public UbicacionEspecieResponse crear(UbicacionEspecieRequest request) {
        Especie especie = especieRepository.findById(request.getIdEspecie())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una especie con id " + request.getIdEspecie()));

        if (!Boolean.TRUE.equals(especie.getActiva())) {
            throw new EstadoInvalidoException(
                    "No se pueden registrar ubicaciones para una especie desactivada.");
        }

        Distrito distrito = distritoRepository.findById(request.getIdDistrito())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un distrito con id " + request.getIdDistrito()));

        UbicacionEspecie ubicacion = UbicacionEspecie.builder()
                .especie(especie)
                .distrito(distrito)
                .nombreLugar(limpiarEspacios(request.getNombreLugar()))
                .latitud(request.getLatitud())
                .longitud(request.getLongitud())
                .descripcion(limpiarEspacios(request.getDescripcion()))
                .fechaObservacion(request.getFechaObservacion())
                .activa(true)
                .build();

        UbicacionEspecie guardada = ubicacionEspecieRepository.save(ubicacion);

        return mapearAResponse(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UbicacionEspecieResponse> obtenerUbicacionesPorEspecie(Long idEspecie) {
        // Sustituye al existsById de antes: ademas de que exista, exige que la
        // ficha se pueda ver. Un borrador responde el mismo 404 que un id que
        // no existe, y aqui los dos textos ya coincidian.
        //
        // Las ubicaciones son el caso en que mas pesa: son coordenadas de donde
        // crece una especie, y las de una ficha sin publicar no tienen por que
        // estar al alcance de cualquiera.
        if (!visibilidadDeEspecie.puedeVer(idEspecie)) {
            throw new RecursoNoEncontradoException("No existe una especie con id " + idEspecie);
        }

        return ubicacionEspecieRepository.findByEspecie_IdEspecieAndActivaTrue(idEspecie)
                .stream()
                .map(this::mapearAResponse)
                .toList();
    }

    @Override
    @Transactional
    public UbicacionEspecieResponse actualizarUbicacion(Long idUbicacion, UbicacionEspecieRequest request) {
        UbicacionEspecie ubicacion = ubicacionEspecieRepository.findById(idUbicacion)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una ubicación con id " + idUbicacion));

        Especie especie = especieRepository.findById(request.getIdEspecie())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una especie con id " + request.getIdEspecie()));

        if (!Boolean.TRUE.equals(especie.getActiva())) {
            throw new EstadoInvalidoException(
                    "No se pueden registrar ubicaciones para una especie desactivada.");
        }

        Distrito distrito = distritoRepository.findById(request.getIdDistrito())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un distrito con id " + request.getIdDistrito()));

        ubicacion.setEspecie(especie);
        ubicacion.setDistrito(distrito);
        ubicacion.setNombreLugar(limpiarEspacios(request.getNombreLugar()));
        ubicacion.setLatitud(request.getLatitud());
        ubicacion.setLongitud(request.getLongitud());
        ubicacion.setDescripcion(limpiarEspacios(request.getDescripcion()));
        ubicacion.setFechaObservacion(request.getFechaObservacion());

        UbicacionEspecie actualizada = ubicacionEspecieRepository.save(ubicacion);
        return mapearAResponse(actualizada);
    }

    @Override
    @Transactional
    public void desactivarUbicacion(Long idUbicacion) {
        UbicacionEspecie ubicacion = ubicacionEspecieRepository.findById(idUbicacion)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una ubicación con id " + idUbicacion));

        ubicacion.setActiva(false);
    }

    private UbicacionEspecieResponse mapearAResponse(UbicacionEspecie entidad) {
        return UbicacionEspecieResponse.builder()
                .idUbicacion(entidad.getIdUbicacion())
                .idEspecie(entidad.getEspecie().getIdEspecie())
                .idDistrito(entidad.getDistrito().getIdDistrito())
                .nombreDistrito(entidad.getDistrito().getNombre())
                .nombreLugar(entidad.getNombreLugar())
                .latitud(entidad.getLatitud())
                .longitud(entidad.getLongitud())
                .descripcion(entidad.getDescripcion())
                .fechaObservacion(entidad.getFechaObservacion())
                .activa(entidad.getActiva())
                .fechaRegistro(entidad.getFechaRegistro())
                .build();
    }

    private String limpiarEspacios(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim().replaceAll("\\s+", " ");
        return limpio.isEmpty() ? null : limpio;
    }
}
