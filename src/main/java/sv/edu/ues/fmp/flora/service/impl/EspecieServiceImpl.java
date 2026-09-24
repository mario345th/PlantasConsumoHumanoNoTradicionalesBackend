package sv.edu.ues.fmp.flora.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.EspecieRequest;
import sv.edu.ues.fmp.flora.dto.request.NombreComunRequest;
import sv.edu.ues.fmp.flora.dto.request.TaxonomiaRequest;
import sv.edu.ues.fmp.flora.dto.response.EspecieResponse;
import sv.edu.ues.fmp.flora.dto.response.NombreComunResponse;
import sv.edu.ues.fmp.flora.entity.Especie;
import sv.edu.ues.fmp.flora.entity.Taxonomia;
import sv.edu.ues.fmp.flora.entity.Usuario;
import sv.edu.ues.fmp.flora.entity.enums.EstadoPublicacion;
import sv.edu.ues.fmp.flora.exception.EstadoInvalidoException;
import sv.edu.ues.fmp.flora.exception.RecursoDuplicadoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.EspecieMapper;
import sv.edu.ues.fmp.flora.mapper.TaxonomiaMapper;
import sv.edu.ues.fmp.flora.repository.EspecieRepository;
import sv.edu.ues.fmp.flora.repository.TaxonomiaRepository;
import sv.edu.ues.fmp.flora.repository.UsuarioRepository;
import sv.edu.ues.fmp.flora.service.EspecieService;
import sv.edu.ues.fmp.flora.service.NombreComunService;

/**
 * Implementacion de la logica de negocio de las especies.
 * <p>
 * Aqui viven la unicidad del nombre cientifico, las transiciones del flujo
 * editorial y la baja logica. Las transiciones se comprueban en Java antes de
 * tocar la entidad porque los CHECK de la base ({@code ck_especie_publicacion},
 * {@code ck_especie_validacion}) solo sabrian rechazar la fila con un error de
 * driver; validando aqui se devuelve un 409 con un mensaje que el cliente
 * entiende.
 */
@Service
@RequiredArgsConstructor
public class EspecieServiceImpl implements EspecieService {

    private final EspecieRepository especieRepository;
    private final TaxonomiaRepository taxonomiaRepository;
    private final UsuarioRepository usuarioRepository;
    private final EspecieMapper especieMapper;
    private final TaxonomiaMapper taxonomiaMapper;

    /**
     * Se depende del servicio de nombres comunes, no de su repositorio, para que
     * los nombres anidados en el POST pasen por exactamente las mismas reglas
     * que los creados por los endpoints del modulo.
     */
    private final NombreComunService nombreComunService;

    @Override
    @Transactional(readOnly = true)
    public List<EspecieResponse> listarTodas() {
        return aRespuestas(especieRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EspecieResponse> listarPublicadas() {
        return aRespuestas(especieRepository
                .findByEstadoPublicacionAndActivaTrue(EstadoPublicacion.PUBLICADA));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EspecieResponse> listarPorEstado(EstadoPublicacion estado) {
        return aRespuestas(especieRepository.findByEstadoPublicacion(estado));
    }

    @Override
    @Transactional(readOnly = true)
    public EspecieResponse obtenerPorId(Long id) {
        return especieMapper.toResponse(buscarOFallar(id));
    }

    @Override
    @Transactional
    public EspecieResponse crear(EspecieRequest request) {
        // La base tiene el indice unico funcional uk_especie_nombre_cientifico_lower
        // sobre lower(nombre_cientifico). Se valida aqui para devolver un 409
        // legible en vez de dejar que estalle la restriccion en el driver.
        if (especieRepository.existsByNombreCientificoIgnoreCase(request.getNombreCientifico())) {
            throw new RecursoDuplicadoException(
                    "Ya existe una especie con el nombre científico " + request.getNombreCientifico());
        }

        Usuario creador = buscarUsuarioOFallar(request.getCreadaPor(), "creador");

        TaxonomiaRequest clasificacion = request.getTaxonomia();

        // Al crear no hay taxonomia propia que excluir de la busqueda.
        validarClasificacionUnica(clasificacion, null);

        // Taxonomia y especie se crean dentro de la MISMA transaccion: si el
        // guardado de la especie falla, el rollback se lleva tambien la
        // taxonomia y no queda un registro huerfano, que es justo lo que el
        // modelo no contempla (id_taxonomia es NOT NULL y UNIQUE).
        Taxonomia taxonomia = taxonomiaRepository.save(
                taxonomiaMapper.toEntity(clasificacion));

        // El estado queda en BORRADOR por el @Builder.Default de la entidad.
        Especie nueva = especieMapper.toEntity(request, taxonomia, creador);

        Especie especieGuardada = especieRepository.save(nueva);

        EspecieResponse respuesta = especieMapper.toResponse(especieGuardada);

        // Los nombres comunes son opcionales: sin ellos la especie se crea igual
        // y se pueden agregar despues por los endpoints de su modulo.
        //
        // Se crean en la MISMA transaccion que la especie y su taxonomia, por el
        // mismo motivo: si uno de ellos falla (nombre repetido dentro de la
        // especie, por ejemplo), el rollback se lleva tambien la especie y la
        // taxonomia, y no queda una ficha a medio poblar que nadie pidio.
        //
        // Se delega en NombreComunService en vez de insertar aqui para no
        // duplicar sus reglas: el desplazamiento del principal anterior, la
        // unicidad de nombre mas region y el flush() que necesita el indice
        // unico parcial ya viven alli. La iteracion es segura respecto a ese
        // flush, y ademas cada crear() consulta antes de insertar, asi que ve
        // los nombres que las vueltas anteriores del bucle ya insertaron.
        List<NombreComunRequest> nombresComunes = request.getNombresComunes();
        if (nombresComunes != null && !nombresComunes.isEmpty()) {
            validarUnPrincipalExacto(nombresComunes);

            List<NombreComunResponse> creados = new ArrayList<>();
            for (NombreComunRequest nombreComun : nombresComunes) {
                creados.add(nombreComunService.crear(especieGuardada.getIdEspecie(), nombreComun));
            }

            // Se colocan a mano porque la coleccion perezosa de la especie
            // recien insertada sigue en null: Hibernate no la puebla con filas
            // que se han insertado despues dentro de la misma sesion, de modo
            // que toResponse() habria devuelto una lista vacia pese a haberlos
            // creado. El orden lo pone el mapper, el mismo que en toResponse().
            respuesta.setNombresComunes(especieMapper.ordenarParaPresentacion(creados));
        }

        return respuesta;
    }

    /**
     * Comprueba que la lista de nombres comunes que acompana a la creacion de
     * una especie traiga uno, y solo uno, marcado como principal.
     * <p>
     * Es {@link EstadoInvalidoException} (409) y no
     * {@link RecursoDuplicadoException}: no hay ningun registro repetido, lo que
     * falla es la composicion del Request frente a la regla de negocio de que
     * una especie tiene exactamente un nombre de cabecera.
     * <p>
     * Se valida antes de insertar nada. Si se dejara al bucle, el segundo
     * principal simplemente desmarcaria al primero y la especie quedaria creada
     * con un principal que el cliente no eligio, en silencio.
     */
    private void validarUnPrincipalExacto(List<NombreComunRequest> nombresComunes) {
        long principales = nombresComunes.stream()
                .filter(nombreComun -> Boolean.TRUE.equals(nombreComun.getEsPrincipal()))
                .count();

        if (principales == 0) {
            throw new EstadoInvalidoException(
                    "Debe marcar exactamente un nombre común como principal. No se marcó ninguno.");
        }
        if (principales > 1) {
            throw new EstadoInvalidoException(
                    "Debe marcar exactamente un nombre común como principal. Se marcaron "
                            + principales + ".");
        }
    }

    @Override
    @Transactional
    public EspecieResponse actualizar(Long id, EspecieRequest request) {
        Especie entidad = buscarOFallar(id);

        Optional<Especie> conMismoNombre =
                especieRepository.findByNombreCientificoIgnoreCase(request.getNombreCientifico());
        if (conMismoNombre.isPresent() && !conMismoNombre.get().getIdEspecie().equals(id)) {
            throw new RecursoDuplicadoException(
                    "Ya existe otra especie con el nombre científico " + request.getNombreCientifico());
        }

        // Este metodo tambien reescribe la taxonomia anidada, asi que puede
        // chocar con uk_taxonomia igual que actualizarTaxonomia(). Se excluye
        // la taxonomia de la propia especie; getTaxonomia() dispara aqui la
        // carga perezosa, dentro de la transaccion.
        Taxonomia taxonomia = entidad.getTaxonomia();
        validarClasificacionUnica(request.getTaxonomia(), taxonomia.getIdTaxonomia());

        // Si el Request trae nombresComunes se ignoran a proposito. Actualizar
        // la ficha no debe reescribir la lista de nombres comunes: un PUT manda
        // el recurso entero, asi que un cliente que solo quisiera corregir la
        // descripcion borraria de hecho todos los nombres que no reenviara.
        // Esa lista se gestiona por los endpoints propios del modulo, que
        // ademas saben aplicar sus reglas una por una.
        especieMapper.updateEntity(entidad, request);
        taxonomiaMapper.updateEntity(taxonomia, request.getTaxonomia());

        // No se llama a save(): dentro de la transaccion la entidad esta
        // gestionada por el contexto de persistencia, asi que Hibernate detecta
        // los cambios (dirty checking) y emite el UPDATE al hacer flush.
        // Ni el estado, ni las fechas, ni los usuarios de validacion o
        // publicacion se tocan aqui: eso solo cambia por las transiciones.
        return especieMapper.toResponse(entidad);
    }

    @Override
    @Transactional
    public EspecieResponse enviarARevision(Long id) {
        Especie entidad = buscarOFallar(id);

        EstadoPublicacion estado = entidad.getEstadoPublicacion();
        if (estado != EstadoPublicacion.BORRADOR && estado != EstadoPublicacion.RECHAZADA) {
            throw new EstadoInvalidoException(
                    "Solo se puede enviar a revisión una especie en BORRADOR o RECHAZADA; "
                            + "la especie " + id + " está en " + estado);
        }

        entidad.setEstadoPublicacion(EstadoPublicacion.EN_REVISION);

        return especieMapper.toResponse(entidad);
    }

    @Override
    @Transactional
    public EspecieResponse validar(Long id, Long idValidador) {
        Especie entidad = buscarOFallar(id);

        if (entidad.getEstadoPublicacion() != EstadoPublicacion.EN_REVISION) {
            throw new EstadoInvalidoException(
                    "Solo se puede validar una especie EN_REVISION; la especie " + id
                            + " está en " + entidad.getEstadoPublicacion());
        }

        Usuario validador = buscarUsuarioOFallar(idValidador, "validador");

        // El CHECK ck_especie_validacion exige que validada_por y
        // fecha_validacion se asignen juntos, nunca uno sin el otro.
        entidad.setValidadaPor(validador);
        entidad.setFechaValidacion(LocalDateTime.now());

        // El estado se mantiene EN_REVISION a proposito: validar no es publicar.

        return especieMapper.toResponse(entidad);
    }

    @Override
    @Transactional
    public EspecieResponse publicar(Long id, Long idPublicador) {
        Especie entidad = buscarOFallar(id);

        // El orden de las dos comprobaciones importa: primero el estado, que es
        // el diagnostico mas informativo para quien llama.
        // Comprobar solo validadaPor no basta: una especie que fue validada y
        // despues rechazada conservaria ese valor, asi que se podria publicar
        // una ficha RECHAZADA. El CHECK ck_especie_publicacion tampoco lo
        // impide, porque solo exige que los tres campos esten llenos y no
        // verifica desde que estado se llega.
        if (entidad.getEstadoPublicacion() != EstadoPublicacion.EN_REVISION) {
            throw new EstadoInvalidoException(
                    "Solo se pueden publicar especies en revisión. Estado actual: "
                            + entidad.getEstadoPublicacion());
        }
        if (entidad.getValidadaPor() == null) {
            throw new EstadoInvalidoException(
                    "La especie debe ser validada antes de publicarse");
        }

        Usuario publicador = buscarUsuarioOFallar(idPublicador, "publicador");

        // El CHECK ck_especie_publicacion exige los tres juntos: validada_por
        // (ya asignado al validar), publicada_por y fecha_publicacion.
        entidad.setEstadoPublicacion(EstadoPublicacion.PUBLICADA);
        entidad.setPublicadaPor(publicador);
        entidad.setFechaPublicacion(LocalDateTime.now());

        return especieMapper.toResponse(entidad);
    }

    @Override
    @Transactional
    public EspecieResponse rechazar(Long id) {
        Especie entidad = buscarOFallar(id);

        if (entidad.getEstadoPublicacion() != EstadoPublicacion.EN_REVISION) {
            throw new EstadoInvalidoException(
                    "Solo se puede rechazar una especie EN_REVISION; la especie " + id
                            + " está en " + entidad.getEstadoPublicacion());
        }

        // Al rechazar se invalida la revision previa: la validacion anterior ya
        // no dice nada sobre la ficha corregida, asi que la especie tendra que
        // volver a validarse cuando regrese a revision. Esto ademas cierra la
        // via por la que una ficha RECHAZADA podia publicarse arrastrando su
        // validada_por antiguo.
        // El CHECK ck_especie_validacion lo permite, porque su condicion es
        // "validada_por IS NULL OR fecha_validacion IS NOT NULL": ambos campos
        // en null la cumplen.
        entidad.setEstadoPublicacion(EstadoPublicacion.RECHAZADA);
        entidad.setValidadaPor(null);
        entidad.setFechaValidacion(null);

        return especieMapper.toResponse(entidad);
    }

    @Override
    @Transactional
    public EspecieResponse actualizarTaxonomia(Long idEspecie, TaxonomiaRequest request) {
        Especie entidad = buscarOFallar(idEspecie);

        // getTaxonomia() dispara aqui la carga perezosa del @OneToOne(LAZY),
        // lo cual es correcto porque ocurre dentro de la transaccion.
        Taxonomia taxonomia = entidad.getTaxonomia();

        // Rectificar la clasificacion puede chocar con la de otra especie, asi
        // que se anticipa uk_taxonomia igual que en crear(), excluyendo la
        // taxonomia que esta especie ya tiene.
        validarClasificacionUnica(request, taxonomia.getIdTaxonomia());

        taxonomiaMapper.updateEntity(taxonomia, request);

        // No se llama a save(): tanto la especie como su taxonomia estan
        // gestionadas por el contexto de persistencia, asi que Hibernate emite
        // el UPDATE por dirty checking al hacer flush.
        return especieMapper.toResponse(entidad);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        Especie entidad = buscarOFallar(id);

        // Baja logica: varias tablas apuntan a especie por llave foranea, un
        // DELETE fisico romperia esas referencias.
        entidad.setActiva(false);
    }

    /**
     * Anticipa la restriccion {@code uk_taxonomia UNIQUE NULLS NOT DISTINCT
     * (reino, genero, especie_taxonomica, subespecie)} para devolver un 409 con
     * un mensaje legible, en vez del 500 que produciria la
     * {@code DataIntegrityViolationException} del driver.
     * <p>
     * Se consulta con el reino YA normalizado por el mapper: si el request trae
     * el reino vacio, lo que se insertara es "Plantae", y buscar por el valor
     * crudo compararia contra algo distinto de lo que se guarda.
     *
     * @param clasificacion      los datos taxonomicos que se pretenden guardar
     * @param idTaxonomiaExcluir id de la taxonomia que la especie ya tiene, para
     *                           que encontrarse a si misma no cuente como
     *                           duplicado al actualizar; {@code null} al crear,
     *                           donde todavia no hay taxonomia propia
     */
    private void validarClasificacionUnica(TaxonomiaRequest clasificacion, Long idTaxonomiaExcluir) {
        taxonomiaRepository.buscarPorClasificacion(
                        taxonomiaMapper.resolverReino(clasificacion.getReino()),
                        clasificacion.getGenero(),
                        clasificacion.getEspecieTaxonomica(),
                        clasificacion.getSubespecie())
                .filter(otra -> !otra.getIdTaxonomia().equals(idTaxonomiaExcluir))
                .ifPresent(otra -> {
                    throw new RecursoDuplicadoException(mensajeClasificacionDuplicada(clasificacion));
                });
    }

    /**
     * Mensaje del 409 por clasificacion repetida. Nombra genero y especie
     * taxonomica, que es lo que el usuario reconoce, sin mencionar la
     * restriccion de la base.
     */
    private String mensajeClasificacionDuplicada(TaxonomiaRequest clasificacion) {
        return "Ya existe una especie registrada con la clasificación "
                + clasificacion.getGenero() + " " + clasificacion.getEspecieTaxonomica();
    }

    private List<EspecieResponse> aRespuestas(List<Especie> entidades) {
        List<EspecieResponse> respuestas = new ArrayList<>();
        for (Especie entidad : entidades) {
            respuestas.add(especieMapper.toResponse(entidad));
        }
        return respuestas;
    }

    private Especie buscarOFallar(Long id) {
        return especieRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una especie con id " + id));
    }

    private Usuario buscarUsuarioOFallar(Long idUsuario, String papel) {
        return usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el usuario " + papel + " con id " + idUsuario));
    }
}
