package sv.edu.ues.fmp.flora.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.ImagenPreparacionRequest;
import sv.edu.ues.fmp.flora.dto.response.ImagenPreparacionResponse;
import sv.edu.ues.fmp.flora.entity.ImagenPreparacion;
import sv.edu.ues.fmp.flora.entity.PreparacionConsumo;
import sv.edu.ues.fmp.flora.exception.EstadoInvalidoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.ImagenPreparacionMapper;
import sv.edu.ues.fmp.flora.repository.ImagenPreparacionRepository;
import sv.edu.ues.fmp.flora.repository.PreparacionConsumoRepository;
import sv.edu.ues.fmp.flora.service.ImagenPreparacionService;
import sv.edu.ues.fmp.flora.service.VisibilidadDeEspecie;

/**
 * Implementacion del servicio para la gestion de imagenes de preparaciones de consumo.
 * <p>
 * Reglas de negocio clave:
 * <ul>
 *   <li>Una preparacion puede tener como maximo una unica imagen principal activa.</li>
 *   <li>Al marcar o registrar una imagen como principal, la que estuviera previamente
 *       marcada se desmarca en la misma transaccion aplicando {@code flush()} para
 *       evitar conflictos con el indice unico parcial de PostgreSQL
 *       {@code uk_imagen_principal_preparacion}.</li>
 *   <li>Se valida la existencia y estado activo de la preparacion padre.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ImagenPreparacionServiceImpl implements ImagenPreparacionService {

    private final ImagenPreparacionRepository imagenPreparacionRepository;
    private final PreparacionConsumoRepository preparacionConsumoRepository;
    private final ImagenPreparacionMapper imagenPreparacionMapper;

    /**
     * Decide si el contenido de la especie padre se puede mostrar a quien
     * llama. Lo que no se puede ver se responde como lo que no existe.
     */
    private final VisibilidadDeEspecie visibilidadDeEspecie;

    @Override
    @Transactional(readOnly = true)
    public List<ImagenPreparacionResponse> listarPorPreparacion(Long idPreparacion, boolean soloActivas) {
        // La preparacion de una ficha sin publicar se responde como una
        // preparacion que no existe: el mismo 404, misma redaccion.
        if (!puedeVerLaPreparacion(idPreparacion)) {
            throw preparacionNoEncontrada(idPreparacion);
        }

        List<ImagenPreparacion> imagenes = soloActivas
                ? imagenPreparacionRepository.findByPreparacionIdPreparacionAndActivaTrueOrderByIdImagenPreparacionAsc(idPreparacion)
                : imagenPreparacionRepository.findByPreparacionIdPreparacionOrderByIdImagenPreparacionAsc(idPreparacion);
        return imagenes.stream().map(imagenPreparacionMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ImagenPreparacionResponse obtenerPorId(Long id) {
        ImagenPreparacion entidad = buscarOFallar(id);

        // La cadena completa: imagen -> preparacion -> parte comestible -> especie.
        if (!visibilidadDeEspecie.puedeVer(entidad.getPreparacion()
                .getEspecieParteComestible().getEspecie().getIdEspecie())) {

            throw noEncontrada(id);
        }

        return imagenPreparacionMapper.toResponse(entidad);
    }

    /** Si la preparacion existe y la especie de la que cuelga se puede ver. */
    private boolean puedeVerLaPreparacion(Long idPreparacion) {
        return preparacionConsumoRepository.findById(idPreparacion)
                .map(preparacion -> visibilidadDeEspecie.puedeVer(
                        preparacion.getEspecieParteComestible().getEspecie().getIdEspecie()))
                .orElse(false);
    }

    /** Los 404 del modulo, cada uno en un solo sitio. */
    private static RecursoNoEncontradoException noEncontrada(Long id) {
        return new RecursoNoEncontradoException("No existe la imagen de preparación con id " + id);
    }

    private static RecursoNoEncontradoException preparacionNoEncontrada(Long idPreparacion) {
        return new RecursoNoEncontradoException(
                "No existe la preparación de consumo con id " + idPreparacion);
    }

    @Override
    public ImagenPreparacionResponse crear(Long idPreparacion, ImagenPreparacionRequest request) {
        PreparacionConsumo preparacion = buscarPreparacionOFallar(idPreparacion);
        verificarPreparacionActiva(preparacion);

        if (Boolean.TRUE.equals(request.getEsPrincipal())) {
            desmarcarPrincipalActual(idPreparacion);
        }

        ImagenPreparacion nueva = imagenPreparacionMapper.toEntity(request, preparacion);
        return imagenPreparacionMapper.toResponse(imagenPreparacionRepository.save(nueva));
    }

    @Override
    public ImagenPreparacionResponse actualizar(Long id, ImagenPreparacionRequest request) {
        ImagenPreparacion entidad = buscarOFallar(id);
        verificarPreparacionActiva(entidad.getPreparacion());
        Long idPreparacion = entidad.getPreparacion().getIdPreparacion();

        boolean promocion = Boolean.TRUE.equals(request.getEsPrincipal())
                && !Boolean.TRUE.equals(entidad.getEsPrincipal());
        if (promocion) {
            verificarImagenActiva(entidad);
            desmarcarPrincipalActual(idPreparacion);
        }

        imagenPreparacionMapper.updateEntity(entidad, request);
        return imagenPreparacionMapper.toResponse(entidad);
    }

    @Override
    public void desactivar(Long id) {
        ImagenPreparacion entidad = buscarOFallar(id);
        entidad.setActiva(false);
        entidad.setEsPrincipal(false);
    }

    @Override
    public ImagenPreparacionResponse activar(Long id) {
        ImagenPreparacion entidad = buscarOFallar(id);
        if (Boolean.TRUE.equals(entidad.getActiva())) {
            return imagenPreparacionMapper.toResponse(entidad);
        }

        verificarPreparacionActiva(entidad.getPreparacion());

        entidad.setEsPrincipal(false);
        entidad.setActiva(true);
        return imagenPreparacionMapper.toResponse(entidad);
    }

    @Override
    public ImagenPreparacionResponse marcarComoPrincipal(Long id) {
        ImagenPreparacion entidad = buscarOFallar(id);
        // El estado de la preparacion se comprueba antes del atajo de
        // idempotencia: con la preparacion inactiva el resto de operaciones ya
        // se rechazaban, pero volver a marcar como principal una imagen que ya
        // lo era respondia 200 (hallazgo IM-H01 del 3er informe de QA).
        verificarPreparacionActiva(entidad.getPreparacion());
        verificarImagenActiva(entidad);

        if (Boolean.TRUE.equals(entidad.getEsPrincipal())) {
            return imagenPreparacionMapper.toResponse(entidad);
        }

        desmarcarPrincipalActual(entidad.getPreparacion().getIdPreparacion());
        entidad.setEsPrincipal(true);

        return imagenPreparacionMapper.toResponse(entidad);
    }

    /**
     * Desmarca la imagen principal activa actual para la preparacion especificada.
     * <p>
     * Aplica {@code flush()} para sincronizar el UPDATE en la base de datos antes de
     * ejecutar un nuevo INSERT o UPDATE que intente promover una imagen.
     * Esto evita violar el indice unico parcial {@code uk_imagen_principal_preparacion}
     * en PostgreSQL (comprobado sentencia a sentencia).
     */
    private void desmarcarPrincipalActual(Long idPreparacion) {
        imagenPreparacionRepository.findByPreparacionIdPreparacionAndEsPrincipalTrueAndActivaTrue(idPreparacion)
                .ifPresent(anterior -> {
                    anterior.setEsPrincipal(false);
                    imagenPreparacionRepository.flush();
                });
    }

    private ImagenPreparacion buscarOFallar(Long id) {
        return imagenPreparacionRepository.findById(id)
                .orElseThrow(() -> noEncontrada(id));
    }

    private PreparacionConsumo buscarPreparacionOFallar(Long idPreparacion) {
        return preparacionConsumoRepository.findById(idPreparacion)
                .orElseThrow(() -> preparacionNoEncontrada(idPreparacion));
    }

    // verificarPreparacionExisteOFallar se retiro: puedeVerLaPreparacion ya
    // trae la preparacion, y comprobar la existencia aparte era una consulta
    // de mas que decia menos.

    private void verificarPreparacionActiva(PreparacionConsumo preparacion) {
        if (!Boolean.TRUE.equals(preparacion.getActiva())) {
            throw new EstadoInvalidoException(
                    "La preparación '" + preparacion.getNombre()
                            + "' está desactivada; no se pueden gestionar sus imágenes.");
        }
    }

    private void verificarImagenActiva(ImagenPreparacion imagen) {
        if (!Boolean.TRUE.equals(imagen.getActiva())) {
            throw new EstadoInvalidoException(
                    "Una imagen desactivada no puede marcarse como principal.");
        }
    }
}

