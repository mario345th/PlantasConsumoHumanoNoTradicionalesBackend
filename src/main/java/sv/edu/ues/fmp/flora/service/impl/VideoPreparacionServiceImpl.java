package sv.edu.ues.fmp.flora.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.VideoPreparacionRequest;
import sv.edu.ues.fmp.flora.dto.response.VideoPreparacionResponse;
import sv.edu.ues.fmp.flora.entity.PreparacionConsumo;
import sv.edu.ues.fmp.flora.entity.VideoPreparacion;
import sv.edu.ues.fmp.flora.exception.EstadoInvalidoException;
import sv.edu.ues.fmp.flora.exception.RecursoDuplicadoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.VideoPreparacionMapper;
import sv.edu.ues.fmp.flora.repository.PreparacionConsumoRepository;
import sv.edu.ues.fmp.flora.repository.VideoPreparacionRepository;
import sv.edu.ues.fmp.flora.service.VideoPreparacionService;
import sv.edu.ues.fmp.flora.service.VisibilidadDeEspecie;

/** Implementacion del servicio para la gestion de videos de preparaciones de consumo. */
@Service
@RequiredArgsConstructor
@Transactional
public class VideoPreparacionServiceImpl implements VideoPreparacionService {

    private final VideoPreparacionRepository videoPreparacionRepository;
    private final PreparacionConsumoRepository preparacionConsumoRepository;
    private final VideoPreparacionMapper videoPreparacionMapper;

    /**
     * Decide si el contenido de la especie padre se puede mostrar a quien
     * llama. Lo que no se puede ver se responde como lo que no existe.
     */
    private final VisibilidadDeEspecie visibilidadDeEspecie;

    @Override
    @Transactional(readOnly = true)
    public List<VideoPreparacionResponse> listarPorPreparacion(Long idPreparacion, boolean soloActivos) {
        // La preparacion de una ficha sin publicar se responde como una
        // preparacion que no existe: el mismo 404, misma redaccion.
        if (!puedeVerLaPreparacion(idPreparacion)) {
            throw preparacionNoEncontrada(idPreparacion);
        }

        List<VideoPreparacion> videos = soloActivos
                ? videoPreparacionRepository.findByPreparacionIdPreparacionAndActivoTrueOrderByIdVideoPreparacionAsc(idPreparacion)
                : videoPreparacionRepository.findByPreparacionIdPreparacionOrderByIdVideoPreparacionAsc(idPreparacion);
        return videos.stream().map(videoPreparacionMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VideoPreparacionResponse obtenerPorId(Long id) {
        VideoPreparacion entidad = buscarOFallar(id);

        // La cadena completa: video -> preparacion -> parte comestible -> especie.
        if (!visibilidadDeEspecie.puedeVer(entidad.getPreparacion()
                .getEspecieParteComestible().getEspecie().getIdEspecie())) {

            throw noEncontrado(id);
        }

        return videoPreparacionMapper.toResponse(entidad);
    }

    /** Si la preparacion existe y la especie de la que cuelga se puede ver. */
    private boolean puedeVerLaPreparacion(Long idPreparacion) {
        return preparacionConsumoRepository.findById(idPreparacion)
                .map(preparacion -> visibilidadDeEspecie.puedeVer(
                        preparacion.getEspecieParteComestible().getEspecie().getIdEspecie()))
                .orElse(false);
    }

    /** Los 404 del modulo, cada uno en un solo sitio. */
    private static RecursoNoEncontradoException noEncontrado(Long id) {
        return new RecursoNoEncontradoException("No existe el video de preparación con id " + id);
    }

    private static RecursoNoEncontradoException preparacionNoEncontrada(Long idPreparacion) {
        return new RecursoNoEncontradoException(
                "No existe la preparación de consumo con id " + idPreparacion);
    }

    @Override
    public VideoPreparacionResponse crear(Long idPreparacion, VideoPreparacionRequest request) {
        PreparacionConsumo preparacion = buscarPreparacionOFallar(idPreparacion);
        verificarPreparacionActiva(preparacion);

        // Se mapea primero para comparar la URL ya normalizada por el mapper,
        // que es la que acabara en la base.
        VideoPreparacion nuevo = videoPreparacionMapper.toEntity(request, preparacion);
        verificarUrlNoDuplicada(idPreparacion, nuevo.getUrlVideo(), null);

        return videoPreparacionMapper.toResponse(videoPreparacionRepository.save(nuevo));
    }

    @Override
    public VideoPreparacionResponse actualizar(Long id, VideoPreparacionRequest request) {
        VideoPreparacion video = buscarOFallar(id);
        verificarPreparacionActiva(video.getPreparacion());
        verificarVideoActivo(video);
        verificarUrlNoDuplicada(video.getPreparacion().getIdPreparacion(),
                normalizarUrl(request.getUrlVideo()), id);

        videoPreparacionMapper.updateEntity(video, request);
        return videoPreparacionMapper.toResponse(video);
    }

    @Override
    public void desactivar(Long id) {
        VideoPreparacion video = buscarOFallar(id);
        if (!Boolean.TRUE.equals(video.getActivo())) {
            throw new EstadoInvalidoException("El video ya está desactivado.");
        }
        video.setActivo(false);
    }

    @Override
    public VideoPreparacionResponse activar(Long id) {
        VideoPreparacion video = buscarOFallar(id);
        if (Boolean.TRUE.equals(video.getActivo())) {
            throw new EstadoInvalidoException("El video ya está activo.");
        }

        verificarPreparacionActiva(video.getPreparacion());
        video.setActivo(true);
        return videoPreparacionMapper.toResponse(video);
    }

    private VideoPreparacion buscarOFallar(Long id) {
        return videoPreparacionRepository.findById(id)
                .orElseThrow(() -> noEncontrado(id));
    }

    private PreparacionConsumo buscarPreparacionOFallar(Long idPreparacion) {
        return preparacionConsumoRepository.findById(idPreparacion)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la preparación de consumo con id " + idPreparacion));
    }

    // verificarPreparacionExisteOFallar se retiro: puedeVerLaPreparacion ya
    // trae la preparacion, y comprobar la existencia aparte era una consulta
    // de mas que decia menos.

    private void verificarPreparacionActiva(PreparacionConsumo preparacion) {
        if (!Boolean.TRUE.equals(preparacion.getActiva())) {
            throw new EstadoInvalidoException(
                    "La preparación '" + preparacion.getNombre()
                            + "' está desactivada; no se pueden gestionar sus videos.");
        }
    }

    /**
     * La baja logica tiene que proteger el registro: un video desactivado
     * conserva su historial pero no se edita hasta reactivarlo (VP-H05).
     */
    private void verificarVideoActivo(VideoPreparacion video) {
        if (!Boolean.TRUE.equals(video.getActivo())) {
            throw new EstadoInvalidoException(
                    "El video está desactivado; reactívelo antes de modificarlo.");
        }
    }

    /**
     * Rechaza repetir la URL de un video activo dentro de la misma
     * preparacion (VP-H03). {@code idExcluido} es el propio registro cuando
     * la comprobacion viene de una actualizacion, para que reenviar su misma
     * URL no choque consigo mismo.
     */
    private void verificarUrlNoDuplicada(Long idPreparacion, String urlVideo, Long idExcluido) {
        if (urlVideo == null) {
            return;
        }
        boolean duplicada = idExcluido == null
                ? videoPreparacionRepository
                        .existsByPreparacionIdPreparacionAndUrlVideoAndActivoTrue(idPreparacion, urlVideo)
                : videoPreparacionRepository
                        .existsByPreparacionIdPreparacionAndUrlVideoAndActivoTrueAndIdVideoPreparacionNot(
                                idPreparacion, urlVideo, idExcluido);
        if (duplicada) {
            throw new RecursoDuplicadoException("El video ya está registrado en esta preparación.");
        }
    }

    /** Misma normalizacion que aplica el mapper antes de guardar. */
    private String normalizarUrl(String urlVideo) {
        if (urlVideo == null) {
            return null;
        }
        String limpia = urlVideo.strip();
        return limpia.isEmpty() ? null : limpia;
    }
}

