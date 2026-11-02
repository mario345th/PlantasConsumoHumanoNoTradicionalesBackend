package sv.edu.ues.fmp.flora.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.VideoEspecieRequest;
import sv.edu.ues.fmp.flora.dto.response.VideoEspecieResponse;
import sv.edu.ues.fmp.flora.entity.Especie;
import sv.edu.ues.fmp.flora.entity.EspecieParteComestible;
import sv.edu.ues.fmp.flora.entity.VideoEspecie;
import sv.edu.ues.fmp.flora.exception.EstadoInvalidoException;
import sv.edu.ues.fmp.flora.exception.RecursoDuplicadoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.VideoEspecieMapper;
import sv.edu.ues.fmp.flora.repository.EspecieRepository;
import sv.edu.ues.fmp.flora.repository.EspecieParteComestibleRepository;
import sv.edu.ues.fmp.flora.repository.VideoEspecieRepository;
import sv.edu.ues.fmp.flora.service.VideoEspecieService;
import sv.edu.ues.fmp.flora.service.VisibilidadDeEspecie;

@Service
@RequiredArgsConstructor
public class VideoEspecieServiceImpl implements VideoEspecieService {

    private final VideoEspecieRepository videoEspecieRepository;
    private final EspecieRepository especieRepository;
    private final EspecieParteComestibleRepository especieParteComestibleRepository;
    private final VideoEspecieMapper videoEspecieMapper;

    /**
     * Decide si el contenido de la especie padre se puede mostrar a quien
     * llama. Lo que no se puede ver se responde como lo que no existe.
     */
    private final VisibilidadDeEspecie visibilidadDeEspecie;

    @Override
    @Transactional(readOnly = true)
    public List<VideoEspecieResponse> listarPorEspecie(Long idEspecie, boolean soloActivos) {
        if (!especieRepository.existsById(idEspecie)) {
            throw new RecursoNoEncontradoException("No existe la especie con id " + idEspecie);
        }
        List<VideoEspecie> videos = soloActivos
                ? videoEspecieRepository.findByEspecieIdEspecieAndActivoTrueOrderByIdVideoAsc(idEspecie)
                : videoEspecieRepository.findByEspecieIdEspecieOrderByIdVideoAsc(idEspecie);
        return videos.stream().map(videoEspecieMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VideoEspecieResponse obtenerPorId(Long id) {
        return videoEspecieMapper.toResponse(buscarVisibleOFallar(id));
    }

    /**
     * El video, pero solo si la especie a la que pertenece se puede ver. Si no,
     * el mismo 404 que un id inexistente, para que no se distingan.
     */
    private VideoEspecie buscarVisibleOFallar(Long id) {
        VideoEspecie entidad = buscarOFallar(id);

        if (!visibilidadDeEspecie.puedeVer(entidad.getEspecie().getIdEspecie())) {
            throw noEncontrado(id);
        }

        return entidad;
    }

    @Override
    @Transactional
    public VideoEspecieResponse crear(Long idEspecie, VideoEspecieRequest request) {
        Especie especie = buscarEspecieActiva(idEspecie);
        EspecieParteComestible parte = resolverParte(idEspecie, request.getIdEspecieParte());
        // Se mapea primero para comparar la URL ya normalizada por el mapper.
        VideoEspecie nuevo = videoEspecieMapper.toEntity(request, especie, parte);
        verificarUrlNoDuplicada(idEspecie, nuevo.getUrlVideo(), null);
        return videoEspecieMapper.toResponse(videoEspecieRepository.save(nuevo));
    }

    @Override
    @Transactional
    public VideoEspecieResponse actualizar(Long id, VideoEspecieRequest request) {
        VideoEspecie video = buscarOFallar(id);
        buscarEspecieActiva(video.getEspecie().getIdEspecie());
        verificarVideoActivo(video);
        Long idEspecie = video.getEspecie().getIdEspecie();
        verificarUrlNoDuplicada(idEspecie, normalizarUrl(request.getUrlVideo()), id);
        EspecieParteComestible parte = resolverParte(idEspecie, request.getIdEspecieParte());
        videoEspecieMapper.updateEntity(video, request, parte);
        return videoEspecieMapper.toResponse(video);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        VideoEspecie video = buscarOFallar(id);
        if (!Boolean.TRUE.equals(video.getActivo())) {
            throw new EstadoInvalidoException("El video ya está desactivado.");
        }
        video.setActivo(false);
    }

    @Override
    @Transactional
    public VideoEspecieResponse activar(Long id) {
        VideoEspecie video = buscarOFallar(id);
        if (Boolean.TRUE.equals(video.getActivo())) {
            throw new EstadoInvalidoException("El video ya está activo.");
        }
        buscarEspecieActiva(video.getEspecie().getIdEspecie());
        resolverParte(video.getEspecie().getIdEspecie(), video.getEspecieParte() == null
                ? null : video.getEspecieParte().getIdEspecieParte());
        video.setActivo(true);
        return videoEspecieMapper.toResponse(video);
    }

    private Especie buscarEspecieActiva(Long idEspecie) {
        Especie especie = especieRepository.findById(idEspecie)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la especie con id " + idEspecie));
        if (!Boolean.TRUE.equals(especie.getActiva())) {
            throw new EstadoInvalidoException("No se pueden gestionar videos de una especie desactivada.");
        }
        return especie;
    }

    private EspecieParteComestible resolverParte(Long idEspecie, Long idEspecieParte) {
        if (idEspecieParte == null) return null;
        EspecieParteComestible parte = especieParteComestibleRepository.findById(idEspecieParte)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la parte comestible con id " + idEspecieParte));
        if (!parte.getEspecie().getIdEspecie().equals(idEspecie)) {
            throw new EstadoInvalidoException(
                    "La parte comestible " + idEspecieParte + " no pertenece a la especie " + idEspecie + ".");
        }
        if (!Boolean.TRUE.equals(parte.getActiva())) {
            throw new EstadoInvalidoException("La parte comestible " + idEspecieParte + " está desactivada.");
        }
        return parte;
    }

    /** Un video dado de baja conserva su historial pero no se edita hasta reactivarlo (VE-H01). */
    private void verificarVideoActivo(VideoEspecie video) {
        if (!Boolean.TRUE.equals(video.getActivo())) {
            throw new EstadoInvalidoException(
                    "El video está desactivado; reactívelo antes de modificarlo.");
        }
    }

    /**
     * Rechaza repetir la URL de un video activo dentro de la misma especie
     * (VE-H03). {@code idExcluido} es el propio registro al actualizarlo.
     */
    private void verificarUrlNoDuplicada(Long idEspecie, String urlVideo, Long idExcluido) {
        if (urlVideo == null) {
            return;
        }
        boolean duplicada = idExcluido == null
                ? videoEspecieRepository.existsByEspecieIdEspecieAndUrlVideoAndActivoTrue(idEspecie, urlVideo)
                : videoEspecieRepository.existsByEspecieIdEspecieAndUrlVideoAndActivoTrueAndIdVideoNot(
                        idEspecie, urlVideo, idExcluido);
        if (duplicada) {
            throw new RecursoDuplicadoException("El video ya está registrado en esta especie.");
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

    private VideoEspecie buscarOFallar(Long id) {
        return videoEspecieRepository.findById(id)
                .orElseThrow(() -> noEncontrado(id));
    }

    /** El 404 del recurso, en un solo sitio. */
    private static RecursoNoEncontradoException noEncontrado(Long id) {
        return new RecursoNoEncontradoException("No existe el video con id " + id);
    }
}
