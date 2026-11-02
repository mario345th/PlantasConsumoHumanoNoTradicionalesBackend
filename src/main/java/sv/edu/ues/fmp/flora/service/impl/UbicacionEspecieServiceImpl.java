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
import sv.edu.ues.fmp.flora.exception.RecursoDuplicadoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.repository.DistritoRepository;
import sv.edu.ues.fmp.flora.repository.EspecieRepository;
import sv.edu.ues.fmp.flora.repository.MunicipioRepository;
import sv.edu.ues.fmp.flora.repository.MunicipioRepository.DepartamentoDeMunicipio;
import sv.edu.ues.fmp.flora.repository.UbicacionEspecieRepository;
import sv.edu.ues.fmp.flora.service.UbicacionEspecieService;
import sv.edu.ues.fmp.flora.service.VisibilidadDeEspecie;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gestion de avistamientos de especies.
 * <p>
 * <strong>AN-H02 — validacion punto-dentro-del-distrito: trabajo futuro.</strong>
 * QA reporto que el sistema acepta coordenadas que no corresponden al distrito
 * indicado en {@code idDistrito}. Comprobarlo de verdad exige saber que
 * territorio ocupa cada distrito, y la base solo guarda su nombre y su
 * municipio: no hay poligonos, ni centroides, ni cajas envolventes por
 * distrito, ni PostGIS instalado. Mientras no existan esos datos, el control
 * que si esta en pie es el de AN-H01 en {@code UbicacionEspecieRequest}, que
 * encierra las coordenadas en la caja de El Salvador. Eso descarta los casos
 * gruesos (otro pais, el mar, el hemisferio equivocado) pero no detecta un
 * punto salvadoreno asignado al distrito vecino.
 */
@Service
@RequiredArgsConstructor
public class UbicacionEspecieServiceImpl implements UbicacionEspecieService {

    /**
     * Una especie dada de baja no gestiona ubicaciones: lo rechazan crear,
     * actualizar y reactivar, y los tres deben decirlo con las mismas palabras.
     */
    private static final String MSG_ESPECIE_DESACTIVADA =
            "No se pueden registrar ubicaciones para una especie desactivada.";

    private final UbicacionEspecieRepository ubicacionEspecieRepository;
    private final EspecieRepository especieRepository;
    private final DistritoRepository distritoRepository;
    private final MunicipioRepository municipioRepository;

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
            throw new EstadoInvalidoException(MSG_ESPECIE_DESACTIVADA);
        }

        Distrito distrito = distritoRepository.findById(request.getIdDistrito())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un distrito con id " + request.getIdDistrito()));

        verificarCoordenadasLibres(request.getIdEspecie(),
                request.getLatitud(), request.getLongitud(), null);

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

        List<UbicacionEspecie> ubicaciones =
                ubicacionEspecieRepository.findActivasPorEspecieConDistrito(idEspecie);
        Map<Long, String> departamentos = departamentosDe(ubicaciones);

        return ubicaciones.stream()
                .map(ubicacion -> mapearAResponse(ubicacion, departamentos))
                .toList();
    }

    @Override
    @Transactional
    public UbicacionEspecieResponse actualizarUbicacion(Long idUbicacion, UbicacionEspecieRequest request) {
        UbicacionEspecie ubicacion = ubicacionEspecieRepository.findById(idUbicacion)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una ubicación con id " + idUbicacion));

        // La baja logica tiene que proteger el registro: QA edito una ubicacion
        // desactivada y el PUT la modifico igual (AN-H03).
        if (!Boolean.TRUE.equals(ubicacion.getActiva())) {
            throw new EstadoInvalidoException(
                    "La ubicación está desactivada; reactívela antes de modificarla.");
        }

        Especie especie = especieRepository.findById(request.getIdEspecie())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una especie con id " + request.getIdEspecie()));

        if (!Boolean.TRUE.equals(especie.getActiva())) {
            throw new EstadoInvalidoException(MSG_ESPECIE_DESACTIVADA);
        }

        Distrito distrito = distritoRepository.findById(request.getIdDistrito())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un distrito con id " + request.getIdDistrito()));

        verificarCoordenadasLibres(request.getIdEspecie(),
                request.getLatitud(), request.getLongitud(), idUbicacion);

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

        // Repetir la baja respondia 204 como la primera vez, sin avisar de que
        // la peticion no cambio nada (AN-H13).
        if (!Boolean.TRUE.equals(ubicacion.getActiva())) {
            throw new EstadoInvalidoException("La ubicación ya está desactivada.");
        }

        ubicacion.setActiva(false);
    }

    @Override
    @Transactional
    public UbicacionEspecieResponse reactivarUbicacion(Long idUbicacion) {
        UbicacionEspecie ubicacion = ubicacionEspecieRepository.findById(idUbicacion)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una ubicación con id " + idUbicacion));

        // Simetrico a desactivarUbicacion (AN-H13): repetir la operacion no
        // cambia nada, y decirlo vale mas que un 200 que finge haber hecho algo.
        if (Boolean.TRUE.equals(ubicacion.getActiva())) {
            throw new EstadoInvalidoException("La ubicación ya está activa.");
        }

        // Misma regla (y mismo mensaje) que crear y actualizar: una especie dada
        // de baja no gestiona ubicaciones, y reactivar una la devolveria a la
        // vista. El estado sale de la asociacion que ya trae la fila, sin volver
        // a consultar el repositorio de especies.
        if (!Boolean.TRUE.equals(ubicacion.getEspecie().getActiva())) {
            throw new EstadoInvalidoException(MSG_ESPECIE_DESACTIVADA);
        }

        // Lo que distingue a reactivar de un simple activa = true: el indice
        // parcial uk_ubicacion_especie_coords_activa (V11) solo deja una fila
        // activa por (especie, latitud, longitud), y mientras esta ubicacion
        // estuvo de baja otra pudo ocupar su sitio. Se excluye a si misma.
        verificarCoordenadasLibres(ubicacion.getEspecie().getIdEspecie(),
                ubicacion.getLatitud(), ubicacion.getLongitud(), idUbicacion);

        ubicacion.setActiva(true);

        // saveAndFlush y no save: adelanta el UPDATE dentro de la transaccion,
        // asi una carrera contra el indice parcial sale como
        // DataIntegrityViolationException (409) y no como un fallo en el commit.
        UbicacionEspecie reactivada = ubicacionEspecieRepository.saveAndFlush(ubicacion);

        return mapearAResponse(reactivada);
    }

    /**
     * Rechaza repetir las coordenadas de una ubicacion activa de la misma
     * especie (AN-H06). {@code idExcluido} es el propio registro cuando la
     * comprobacion viene de una actualizacion o de una reactivacion.
     */
    private void verificarCoordenadasLibres(Long idEspecie, BigDecimal latitud,
                                            BigDecimal longitud, Long idExcluido) {
        if (latitud == null || longitud == null) {
            return;
        }
        boolean ocupadas = idExcluido == null
                ? ubicacionEspecieRepository
                        .existsByEspecieIdEspecieAndLatitudAndLongitudAndActivaTrue(
                                idEspecie, latitud, longitud)
                : ubicacionEspecieRepository
                        .existsByEspecieIdEspecieAndLatitudAndLongitudAndActivaTrueAndIdUbicacionNot(
                                idEspecie, latitud, longitud, idExcluido);
        if (ocupadas) {
            throw new RecursoDuplicadoException(
                    "Ya existe una ubicación de esta especie en esas coordenadas");
        }
    }

    /** Resuelve de una sola consulta el departamento de todos los distritos implicados. */
    private Map<Long, String> departamentosDe(Collection<UbicacionEspecie> ubicaciones) {
        Set<Long> idsMunicipio = ubicaciones.stream()
                .map(UbicacionEspecie::getDistrito)
                .filter(Objects::nonNull)
                .map(Distrito::getIdMunicipio)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (idsMunicipio.isEmpty()) {
            return Map.of();
        }

        return municipioRepository.findDepartamentosPorMunicipio(idsMunicipio).stream()
                .filter(fila -> fila.getNombreDepartamento() != null)
                .collect(Collectors.toMap(DepartamentoDeMunicipio::getIdMunicipio,
                        DepartamentoDeMunicipio::getNombreDepartamento,
                        (uno, otro) -> uno));
    }

    private UbicacionEspecieResponse mapearAResponse(UbicacionEspecie entidad) {
        return mapearAResponse(entidad, departamentosDe(List.of(entidad)));
    }

    private UbicacionEspecieResponse mapearAResponse(UbicacionEspecie entidad,
                                                     Map<Long, String> departamentos) {
        Distrito distrito = entidad.getDistrito();

        return UbicacionEspecieResponse.builder()
                .idUbicacion(entidad.getIdUbicacion())
                .idEspecie(entidad.getEspecie().getIdEspecie())
                .idDistrito(distrito.getIdDistrito())
                .nombreDistrito(distrito.getNombre())
                // AN-H05: el campo existia en la respuesta pero nunca se llenaba.
                .departamentoDistrito(departamentos.get(distrito.getIdMunicipio()))
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
