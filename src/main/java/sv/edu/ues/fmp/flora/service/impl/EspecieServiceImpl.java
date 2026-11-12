package sv.edu.ues.fmp.flora.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
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
import sv.edu.ues.fmp.flora.service.UsuarioAutenticado;
import sv.edu.ues.fmp.flora.service.VisibilidadDeEspecie;
import sv.edu.ues.fmp.flora.util.Restricciones;
import sv.edu.ues.fmp.flora.util.Textos;

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

    /**
     * Quien esta llamando. Es la UNICA fuente de {@code creada_por},
     * {@code validada_por} y {@code publicada_por}: esos tres campos ya no
     * entran por el cuerpo ni por la query.
     */
    private final UsuarioAutenticado usuarioAutenticado;

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
    @Transactional(readOnly = true)
    public EspecieResponse obtenerPublicadaPorId(Long id) {
        // El filtro va en la consulta y no sobre la entidad ya cargada para no
        // dar lugar a un 200 por descuido si manana alguien agrega un estado
        // nuevo al enum: lo que no sea PUBLICADA y activa no entra.
        // El 404 sale de VisibilidadDeEspecie para que este detalle y los
        // sub-recursos respondan exactamente igual; si los textos se
        // separaran, comparar las dos respuestas volveria a delatar los
        // borradores.
        Especie publicada = especieRepository
                .findByIdEspecieAndEstadoPublicacionAndActivaTrue(id, EstadoPublicacion.PUBLICADA)
                .orElseThrow(() -> VisibilidadDeEspecie.noEncontrada(id));

        return especieMapper.toResponse(publicada);
    }

    @Override
    @Transactional
    public EspecieResponse crear(EspecieRequest request) {
        normalizar(request);

        // La base tiene el indice unico funcional uk_especie_nombre_cientifico_lower
        // sobre lower(nombre_cientifico). Se valida aqui para devolver un 409
        // legible en vez de dejar que estalle la restriccion en el driver.
        if (especieRepository.existsByNombreCientificoIgnoreCase(request.getNombreCientifico())) {
            throw new RecursoDuplicadoException(
                    "Ya existe una especie con el nombre científico " + request.getNombreCientifico());
        }

        // El creador es quien trae el token, nunca un id del cuerpo: ver
        // UsuarioAutenticado. Que ese usuario exista se sigue comprobando
        // igual, y el 404 es el mismo de antes.
        Usuario creador = buscarUsuarioOFallar(usuarioAutenticado.id(), "creador");

        TaxonomiaRequest clasificacion = request.getTaxonomia();

        // Al crear no hay taxonomia propia que excluir de la busqueda.
        validarClasificacionUnica(clasificacion, null);

        // Taxonomia y especie se crean dentro de la MISMA transaccion: si el
        // guardado de la especie falla, el rollback se lleva tambien la
        // taxonomia y no queda un registro huerfano, que es justo lo que el
        // modelo no contempla (id_taxonomia es NOT NULL y UNIQUE).
        Taxonomia taxonomia = guardarClasificacion(clasificacion);

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
        normalizar(request);

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
        // los cambios (dirty checking) y emite el UPDATE al hacer flush, que
        // responderActualizada() adelanta.
        // Ni el estado, ni las fechas, ni los usuarios de validacion o
        // publicacion se tocan aqui: eso solo cambia por las transiciones.
        return responderActualizada(entidad, request.getTaxonomia());
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

        return responderActualizada(entidad);
    }

    @Override
    @Transactional
    public EspecieResponse validar(Long id) {
        Especie entidad = buscarOFallar(id);

        if (entidad.getEstadoPublicacion() != EstadoPublicacion.EN_REVISION) {
            throw new EstadoInvalidoException(
                    "Solo se puede validar una especie EN_REVISION; la especie " + id
                            + " está en " + entidad.getEstadoPublicacion());
        }

        // El estado por si solo no basta como guarda: una especie ya validada
        // sigue EN_REVISION hasta que se publique o se rechace, asi que sin
        // esta comprobacion un segundo validador sobrescribiria al primero y
        // la ficha quedaria atribuida a quien llamo de ultimo. Rechazar es la
        // unica via que limpia validadaPor, y por eso es la que indica el
        // mensaje.
        if (entidad.getValidadaPor() != null) {
            throw new EstadoInvalidoException(
                    "La especie " + id + " ya fue validada por "
                            + entidad.getValidadaPor().getNombreUsuario()
                            + " el " + entidad.getFechaValidacion()
                            + ". Para asignar un nuevo validador, rechácela primero.");
        }

        // El validador es quien trae el token. Antes llegaba en la query, de
        // modo que quien tenia ESPECIE_VALIDAR podia atribuirle la validacion
        // a cualquier otro usuario.
        Usuario validador = buscarUsuarioOFallar(usuarioAutenticado.id(), "validador");

        // El CHECK ck_especie_validacion exige que validada_por y
        // fecha_validacion se asignen juntos, nunca uno sin el otro.
        entidad.setValidadaPor(validador);
        entidad.setFechaValidacion(LocalDateTime.now());

        // El estado se mantiene EN_REVISION a proposito: validar no es publicar.

        return responderActualizada(entidad);
    }

    @Override
    @Transactional
    public EspecieResponse publicar(Long id) {
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

        // Mismo criterio que el validador: sale del token.
        Usuario publicador = buscarUsuarioOFallar(usuarioAutenticado.id(), "publicador");

        // El CHECK ck_especie_publicacion exige los tres juntos: validada_por
        // (ya asignado al validar), publicada_por y fecha_publicacion.
        entidad.setEstadoPublicacion(EstadoPublicacion.PUBLICADA);
        entidad.setPublicadaPor(publicador);
        entidad.setFechaPublicacion(LocalDateTime.now());

        return responderActualizada(entidad);
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

        return responderActualizada(entidad);
    }

    @Override
    @Transactional
    public EspecieResponse actualizarTaxonomia(Long idEspecie, TaxonomiaRequest request) {
        normalizar(request);

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
        // el UPDATE por dirty checking al hacer flush, que responderActualizada()
        // adelanta.
        return responderActualizada(entidad, request);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        Especie entidad = buscarOFallar(id);

        // Baja logica: varias tablas apuntan a especie por llave foranea, un
        // DELETE fisico romperia esas referencias.
        entidad.setActiva(false);
    }

    @Override
    @Transactional
    public EspecieResponse activar(Long id) {
        Especie entidad = buscarOFallar(id);

        // Idempotente: si ya esta activa no hay nada que cambiar, ni UPDATE
        // pendiente, asi que no hace falta el flush de responderActualizada().
        if (Boolean.TRUE.equals(entidad.getActiva())) {
            return especieMapper.toResponse(entidad);
        }

        // No se toca estadoPublicacion: la ficha recupera el punto del flujo
        // editorial en el que estaba. Si era PUBLICADA, reaparece en el
        // catalogo publico en cuanto se confirma esta transaccion.
        entidad.setActiva(true);
        return responderActualizada(entidad);
    }

    /**
     * Arma la respuesta de un metodo que modifico una especie existente,
     * forzando antes el flush de los cambios pendientes.
     * <p>
     * {@code fecha_actualizacion} la escribe el trigger BEFORE UPDATE
     * {@code trg_especie_fecha_actualizacion}, y el {@code @Generated} de la
     * entidad solo relee ese valor despues de ejecutar el UPDATE. Sin el flush,
     * el UPDATE ocurriria al hacer commit, cuando la respuesta ya se armo con la
     * fecha anterior.
     * <p>
     * {@link #crear} no lo necesita: con IDENTITY el INSERT se ejecuta en el
     * propio {@code save()}, y las fechas se releen en ese momento.
     */
    private EspecieResponse responderActualizada(Especie especie) {
        especieRepository.flush();
        return especieMapper.toResponse(especie);
    }

    /**
     * Lo mismo, para los dos metodos que ademas reescriben la taxonomia, de
     * modo que el choque con {@code uk_taxonomia} que pudiera salir del flush
     * salga como el 409 de siempre y no como un 500.
     *
     * @param clasificacion los datos que se intentaban guardar, solo para el
     *                      mensaje
     */
    private EspecieResponse responderActualizada(Especie especie, TaxonomiaRequest clasificacion) {
        try {
            return responderActualizada(especie);
        } catch (DataIntegrityViolationException choque) {
            throw conflictoDeClasificacion(clasificacion, choque);
        }
    }

    /**
     * Inserta la taxonomia nueva traduciendo el choque con {@code uk_taxonomia}
     * al mismo 409 que devuelve la comprobacion previa.
     */
    private Taxonomia guardarClasificacion(TaxonomiaRequest clasificacion) {
        try {
            return taxonomiaRepository.save(taxonomiaMapper.toEntity(clasificacion));
        } catch (DataIntegrityViolationException choque) {
            throw conflictoDeClasificacion(clasificacion, choque);
        }
    }

    /**
     * La red de seguridad de {@link #validarClasificacionUnica}.
     *
     * <h2>Por que hace falta si ya se consulta antes</h2>
     * {@code validarClasificacionUnica} consulta y despues se inserta, asi que
     * entre las dos sentencias cabe otra transaccion: dos peticiones
     * simultaneas con la misma clasificacion pasan las dos la comprobacion y
     * es la base la que rechaza a la segunda. Verificado contra la base de
     * desarrollo con dos sesiones en paralelo: la segunda se queda esperando
     * en el indice {@code uk_taxonomia} y, al confirmar la primera, falla con
     * {@code llave duplicada viola restriccion de unicidad «uk_taxonomia»},
     * tambien cuando {@code subespecie} es NULL en las dos --el indice es
     * {@code NULLS NOT DISTINCT}, de modo que dos NULL cuentan como iguales--.
     * Sin este catch, esa carrera devolvia el 409 generico del
     * {@code GlobalExceptionHandler} ("La operacion viola una restriccion de
     * la base de datos"), que no dice que clasificacion choco.
     * <p>
     * La especie no queda a medias: el metodo es {@code @Transactional} y la
     * excepcion sale de el, asi que el rollback se lleva la taxonomia, la
     * especie y los nombres comunes que se hubieran insertado.
     *
     * @return la excepcion que hay que lanzar: el 409 legible si el choque fue
     *         con {@code uk_taxonomia}, o el error original tal cual si fue
     *         con cualquier otra restriccion --el nombre cientifico, por
     *         ejemplo-- para que lo traduzca quien sabe de ella
     */
    private RuntimeException conflictoDeClasificacion(
            TaxonomiaRequest clasificacion, DataIntegrityViolationException choque) {

        if (Restricciones.violacionDe(choque, "uk_taxonomia")) {
            return new RecursoDuplicadoException(mensajeClasificacionDuplicada(clasificacion));
        }
        return choque;
    }

    /**
     * Normaliza sobre el propio Request los textos que participan en
     * comprobaciones de unicidad, para que la consulta de duplicados y el
     * INSERT/UPDATE usen exactamente el mismo valor. Debe llamarse antes de
     * cualquier consulta; ver {@link Textos}.
     */
    private void normalizar(EspecieRequest request) {
        request.setNombreCientifico(Textos.normalizar(request.getNombreCientifico()));
        normalizar(request.getTaxonomia());
    }

    /**
     * Normaliza TODOS los campos de texto de la clasificacion, no solo los dos
     * que participan en {@code uk_taxonomia}.
     *
     * <h2>Por que los ocho y no solo genero y especie</h2>
     * Antes se normalizaban genero y especie taxonomica, porque son los que se
     * comparan al buscar duplicados. Los otros seis se guardaban tal como
     * llegaban, y eso rompia dos cosas:
     * <ul>
     *   <li><strong>El reino.</strong> Su validacion de formato
     *       ({@code @Pattern} en {@code TaxonomiaRequest}) acepta espacios
     *       alrededor, de modo que {@code " Plantae "} pasaba el 400 y
     *       llegaba al CHECK {@code ck_taxonomia_reino}, que compara
     *       {@code lower(reino) = 'plantae'} exacto: la base lo rechazaba y
     *       salia un 500. Ahora se guarda {@code "Plantae"}.</li>
     *   <li><strong>Los demas.</strong> {@code " Fabaceae "} y
     *       {@code "Fabaceae"} se guardaban como familias distintas, asi que
     *       el mismo dato quedaba escrito de dos formas segun quien lo
     *       capturara.</li>
     * </ul>
     * {@link Textos#normalizar} quita los espacios de los extremos, colapsa
     * los internos y devuelve null si no queda nada; para el reino ese null es
     * justo lo que {@code TaxonomiaMapper.resolverReino} convierte en
     * "Plantae", y para los opcionales es la columna vacia que corresponde en
     * vez de una cadena de espacios.
     * <p>
     * Queda una arista conocida: las validaciones de longitud
     * ({@code @Size}) corren en el controlador, antes de este trim, asi que un
     * valor que solo excede el limite por los espacios se rechaza con 400 en
     * vez de recortarse. Se deja asi porque mover el trim al DTO pondria
     * logica en un objeto que hoy es solo datos, y porque el caso --101
     * caracteres de los que sobran espacios-- no es el que reporto QA.
     */
    private void normalizar(TaxonomiaRequest clasificacion) {
        clasificacion.setReino(Textos.normalizar(clasificacion.getReino()));
        clasificacion.setDivision(Textos.normalizar(clasificacion.getDivision()));
        clasificacion.setClase(Textos.normalizar(clasificacion.getClase()));
        clasificacion.setOrdenTaxonomico(Textos.normalizar(clasificacion.getOrdenTaxonomico()));
        clasificacion.setFamilia(Textos.normalizar(clasificacion.getFamilia()));
        clasificacion.setGenero(Textos.normalizar(clasificacion.getGenero()));
        clasificacion.setEspecieTaxonomica(Textos.normalizar(clasificacion.getEspecieTaxonomica()));
        clasificacion.setSubespecie(Textos.normalizar(clasificacion.getSubespecie()));
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
