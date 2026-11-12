package sv.edu.ues.fmp.flora.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.persistence.EntityManager;
import sv.edu.ues.fmp.flora.entity.AporteNutricional;
import sv.edu.ues.fmp.flora.entity.Beneficio;
import sv.edu.ues.fmp.flora.entity.Distrito;
import sv.edu.ues.fmp.flora.entity.EpocaCosecha;
import sv.edu.ues.fmp.flora.entity.Especie;
import sv.edu.ues.fmp.flora.entity.EspecieFuente;
import sv.edu.ues.fmp.flora.entity.EspecieFuenteId;
import sv.edu.ues.fmp.flora.entity.EspecieHabitat;
import sv.edu.ues.fmp.flora.entity.EspecieHabitatId;
import sv.edu.ues.fmp.flora.entity.EspecieParteComestible;
import sv.edu.ues.fmp.flora.entity.Fuente;
import sv.edu.ues.fmp.flora.entity.Habitat;
import sv.edu.ues.fmp.flora.entity.ImagenEspecie;
import sv.edu.ues.fmp.flora.entity.ImagenPreparacion;
import sv.edu.ues.fmp.flora.entity.NombreComun;
import sv.edu.ues.fmp.flora.entity.Nutriente;
import sv.edu.ues.fmp.flora.entity.ParteBeneficio;
import sv.edu.ues.fmp.flora.entity.PartePlanta;
import sv.edu.ues.fmp.flora.entity.PreparacionConsumo;
import sv.edu.ues.fmp.flora.entity.Taxonomia;
import sv.edu.ues.fmp.flora.entity.UbicacionEspecie;
import sv.edu.ues.fmp.flora.entity.Usuario;
import sv.edu.ues.fmp.flora.entity.VideoEspecie;
import sv.edu.ues.fmp.flora.entity.VideoPreparacion;
import sv.edu.ues.fmp.flora.entity.enums.CategoriaNutriente;
import sv.edu.ues.fmp.flora.entity.enums.EstadoPublicacion;
import sv.edu.ues.fmp.flora.entity.enums.ParteBeneficioId;
import sv.edu.ues.fmp.flora.entity.enums.TipoBeneficio;
import sv.edu.ues.fmp.flora.entity.enums.TipoConsumo;
import sv.edu.ues.fmp.flora.entity.enums.TipoFuente;
import sv.edu.ues.fmp.flora.entity.enums.TipoImagen;

/**
 * Siembra una ficha de especie completa con el estado de publicacion que se le
 * pida, para que las pruebas de visibilidad no dependan de lo que haya en la
 * base de quien las corra.
 *
 * <h2>El problema que resuelve (H1 de la auditoria)</h2>
 * {@link EspecieVisibilidadPublicaTest},
 * {@link EspecieSubRecursosVisibilidadTest} y
 * {@link EspecieRutasPlanasVisibilidadTest} buscaban una ficha en BORRADOR o
 * EN_REVISION entre los datos sembrados por las migraciones y, cuando no la
 * encontraban, se saltaban con {@code Assumptions.assumeTrue}. {@code V14}
 * recargo el catalogo dejando las quince fichas en PUBLICADA, asi que no habia
 * ninguna y <strong>dieciseis pruebas pasaron a saltarse en silencio</strong>:
 * justo las de la fuga de borradores, dentro de un BUILD SUCCESS que no
 * delataba nada. Sembrando aqui, las pruebas corren siempre y fallan si el
 * guard se rompe, que es lo que se les pide.
 *
 * <h2>Por que con EntityManager y no con los repositorios</h2>
 * Un arbol completo toca catorce tablas. Pedirle a cada clase de prueba que
 * inyecte catorce repositorios para sembrarlo seria mas ruido que el de las
 * propias pruebas; con el {@link EntityManager} de la transaccion basta una
 * dependencia. Es el mismo {@code EntityManager} que usan los repositorios por
 * debajo, de modo que un {@code findAll()} posterior ve lo sembrado.
 *
 * <h2>Por que no queda residuo</h2>
 * Las tres clases son {@code @SpringBootTest @Transactional}: todo lo que se
 * inserta aqui vive en la transaccion de la prueba y el rollback se lo lleva.
 * No hay {@code commit} en ninguna parte, ni {@code @Commit}, ni
 * {@code TransactionTemplate}. El unico rastro que queda en la base es el
 * avance de las secuencias de ids, que es inevitable con
 * {@code GenerationType.IDENTITY} y no es un dato.
 * <p>
 * Esta clase no es un bean: se construye a mano en el {@code @BeforeEach} de
 * cada prueba. Sin {@code @Component} a proposito, para que el escaneo de
 * componentes no la meta en el contexto de toda la suite.
 *
 * <h2>Lo que reutiliza y lo que crea</h2>
 * Los catalogos independientes (parte de planta, habitat, nutriente,
 * beneficio, fuente, distrito) se reutilizan si la base los tiene y se crean si
 * no, de modo que la siembra funciona igual en una base recargada que en una
 * vacia. Lo que es de la ficha --taxonomia, nombre comun, parte comestible,
 * imagenes, videos, ubicacion, epoca, aporte, preparacion-- se crea siempre
 * nuevo: es lo que las pruebas van a mirar y tiene que ser suyo.
 */
public final class SiembraDeFichas {

    /**
     * Distingue los nombres de una siembra de los de otra. Los indices unicos
     * del esquema --{@code uk_especie_nombre_cientifico_lower},
     * {@code uk_taxonomia}, {@code uk_video_especie_url_activa},
     * {@code uk_video_preparacion_url_activa}-- no entienden de transacciones:
     * dos siembras dentro de la misma prueba chocarian entre si si compartieran
     * el nombre cientifico o la URL.
     */
    private static final AtomicLong SECUENCIA = new AtomicLong();

    private final EntityManager em;

    public SiembraDeFichas(EntityManager em) {
        this.em = em;
    }

    /**
     * Lo que quedo sembrado. Las pruebas piden de aqui los ids que necesitan en
     * vez de buscarlos en la base, que es la diferencia entre una prueba
     * determinista y una que se salta.
     */
    public record Ficha(
            Especie especie,
            NombreComun nombreComun,
            EspecieParteComestible parte,
            ImagenEspecie imagenEspecie,
            VideoEspecie videoEspecie,
            UbicacionEspecie ubicacion,
            EpocaCosecha epocaCosecha,
            AporteNutricional aporte,
            ParteBeneficio parteBeneficio,
            PreparacionConsumo preparacion,
            ImagenPreparacion imagenPreparacion,
            VideoPreparacion videoPreparacion) {

        public Long id() {
            return especie.getIdEspecie();
        }

        public EstadoPublicacion estado() {
            return especie.getEstadoPublicacion();
        }
    }

    /**
     * Siembra una ficha con el estado pedido y todo su arbol de hijos.
     *
     * @param estado BORRADOR, EN_REVISION, RECHAZADA o PUBLICADA. Para
     *               PUBLICADA se rellenan tambien validada_por, publicada_por y
     *               las dos fechas, porque los CHECK
     *               {@code ck_especie_publicacion} y
     *               {@code ck_especie_validacion} los exigen juntos.
     */
    public Ficha sembrar(EstadoPublicacion estado) {
        long n = SECUENCIA.incrementAndGet();

        Usuario responsable = responsable();

        Taxonomia taxonomia = persistir(Taxonomia.builder()
                .reino("Plantae")
                .familia("Pruebaceae")
                .genero("Zzsiembra" + n)
                .especieTaxonomica("visibilidad")
                .build());

        Especie especie = Especie.builder()
                .taxonomia(taxonomia)
                .nombreCientifico("Zzsiembra" + n + " visibilidad")
                .descripcion("Ficha sembrada por las pruebas de visibilidad (" + estado + ").")
                .estadoPublicacion(estado)
                .activa(true)
                .creadaPor(responsable)
                .build();

        if (estado == EstadoPublicacion.PUBLICADA) {
            LocalDateTime ahora = LocalDateTime.now();
            especie.setValidadaPor(responsable);
            especie.setFechaValidacion(ahora);
            especie.setPublicadaPor(responsable);
            especie.setFechaPublicacion(ahora);
        }

        persistir(especie);

        NombreComun nombreComun = persistir(NombreComun.builder()
                .especie(especie)
                .nombre("Siembra " + n)
                .region("Prueba")
                .esPrincipal(true)
                .activo(true)
                .build());

        EspecieParteComestible parte = persistir(EspecieParteComestible.builder()
                .especie(especie)
                .partePlanta(partePlanta())
                .descripcion("Parte comestible sembrada por las pruebas.")
                .activa(true)
                .build());

        ImagenEspecie imagenEspecie = persistir(ImagenEspecie.builder()
                .especie(especie)
                .tipoImagen(TipoImagen.PLANTA_COMPLETA)
                .urlImagen("https://example.invalid/siembra-" + n + ".jpg")
                .textoAlternativo("Imagen sembrada por las pruebas")
                // Sin principal: el esquema tiene un unico parcial para la
                // imagen principal y aqui no hace falta ocuparlo.
                .esPrincipal(false)
                .activa(true)
                .build());

        VideoEspecie videoEspecie = persistir(VideoEspecie.builder()
                .especie(especie)
                .titulo("Video sembrado " + n)
                .urlVideo("https://example.invalid/siembra-especie-" + n + ".mp4")
                .activo(true)
                .build());

        EpocaCosecha epocaCosecha = persistir(EpocaCosecha.builder()
                .especieParteComestible(parte)
                .mesInicio((short) 1)
                .mesFin((short) 3)
                .observacion("Epoca sembrada por las pruebas.")
                .activa(true)
                .build());

        AporteNutricional aporte = persistir(AporteNutricional.builder()
                .especieParteComestible(parte)
                .nutriente(nutriente())
                .fuente(fuente())
                .cantidad(new BigDecimal("1.0000"))
                .unidadMedida("g")
                .porcionReferencia("100 g")
                .build());

        ParteBeneficio parteBeneficio = sembrarParteBeneficio(parte);

        PreparacionConsumo preparacion = persistir(PreparacionConsumo.builder()
                .especieParteComestible(parte)
                .nombre("Preparación sembrada " + n)
                .tipoConsumo(TipoConsumo.CRUDO)
                .procedimiento("Procedimiento sembrado por las pruebas.")
                .ordenPresentacion((short) 1)
                .activa(true)
                .build());

        ImagenPreparacion imagenPreparacion = persistir(ImagenPreparacion.builder()
                .preparacion(preparacion)
                .urlImagen("https://example.invalid/siembra-preparacion-" + n + ".jpg")
                .textoAlternativo("Imagen de preparación sembrada por las pruebas")
                .esPrincipal(false)
                .activa(true)
                .build());

        VideoPreparacion videoPreparacion = persistir(VideoPreparacion.builder()
                .preparacion(preparacion)
                .titulo("Video de preparación sembrado " + n)
                .urlVideo("https://example.invalid/siembra-preparacion-" + n + ".mp4")
                .activo(true)
                .build());

        // Los dos de clave compuesta cierran los seis sub-recursos anidados:
        // sin ellos, /habitats y /fuentes de la ficha sembrada responderian la
        // lista vacia y la prueba no distinguiria "no se ve" de "no hay".
        persistir(EspecieHabitat.builder()
                .id(new EspecieHabitatId(especie.getIdEspecie(), habitat().getIdHabitat()))
                .especie(especie)
                .habitat(habitat())
                .observacion("Hábitat sembrado por las pruebas.")
                .build());

        persistir(EspecieFuente.builder()
                .id(new EspecieFuenteId(especie.getIdEspecie(), fuente().getIdFuente()))
                .especie(especie)
                .fuente(fuente())
                .observacion("Fuente sembrada por las pruebas.")
                .build());

        UbicacionEspecie ubicacion = sembrarUbicacion(especie, n);

        // Que todo quede en la base antes de la primera peticion: MockMvc corre
        // en la misma transaccion, pero las consultas del controlador son SQL y
        // no verian lo que siguiera en la cola de inserciones de Hibernate.
        em.flush();

        return new Ficha(especie, nombreComun, parte, imagenEspecie, videoEspecie, ubicacion,
                epocaCosecha, aporte, parteBeneficio, preparacion, imagenPreparacion,
                videoPreparacion);
    }

    private ParteBeneficio sembrarParteBeneficio(EspecieParteComestible parte) {
        Beneficio beneficio = beneficio();

        return persistir(ParteBeneficio.builder()
                .id(new ParteBeneficioId(parte.getIdEspecieParte(), beneficio.getIdBeneficio()))
                .especieParteComestible(parte)
                .beneficio(beneficio)
                .observacion("Beneficio sembrado por las pruebas.")
                .activa(true)
                .build());
    }

    /**
     * La ubicacion es el unico hijo que puede no sembrarse: necesita un
     * distrito, y un distrito necesita municipio y departamento, una cadena que
     * no vale la pena inventar. Las migraciones {@code V7} y {@code V8} cargan
     * los 262 distritos, asi que en la practica siempre hay uno.
     * <p>
     * Que falte no deja ninguna prueba sin ejecutar: la ruta
     * {@code /api/ubicaciones/especie/{id}} lleva el id de la especie, no el de
     * la ubicacion, y responde igual con la lista vacia.
     */
    private UbicacionEspecie sembrarUbicacion(Especie especie, long n) {
        return primero("select d from Distrito d order by d.idDistrito", Distrito.class)
                .map(distrito -> persistir(UbicacionEspecie.builder()
                        .especie(especie)
                        .distrito(distrito)
                        .nombreLugar("Lugar sembrado " + n)
                        // Dentro de El Salvador y distintas en cada siembra, por
                        // el indice unico parcial (id_especie, latitud, longitud)
                        // de V11.
                        .latitud(new BigDecimal("13.6000000"))
                        .longitud(new BigDecimal("-88.8000000").add(
                                new BigDecimal("0.0000001").multiply(BigDecimal.valueOf(n))))
                        .fechaObservacion(LocalDate.now())
                        .activa(true)
                        .build()))
                .orElse(null);
    }

    // ---------- Catalogos: se reutiliza lo que haya, se crea si no ----------

    /**
     * El usuario al que se atribuye la ficha. Se reutiliza uno activo porque
     * {@code creada_por} es NOT NULL y el nombre de usuario y el correo son
     * unicos; crear uno solo para esto obligaria a inventar un hash de clave.
     */
    private Usuario responsable() {
        return primero("select u from Usuario u where u.activo = true order by u.idUsuario",
                Usuario.class)
                .orElseGet(() -> persistir(Usuario.builder()
                        .nombres("Siembra")
                        .apellidos("De Pruebas")
                        .correo("siembra.pruebas." + SECUENCIA.incrementAndGet() + "@prueba.local")
                        .nombreUsuario("siembra.pruebas." + SECUENCIA.get())
                        // No es una clave usable: nadie inicia sesion con este
                        // usuario, solo firma la ficha sembrada.
                        .claveHash("$2a$10$siembraDePruebasSinClaveUtilizable000000000000000000000")
                        .activo(true)
                        .build()));
    }

    private PartePlanta partePlanta() {
        return primero("select p from PartePlanta p order by p.idPartePlanta", PartePlanta.class)
                .orElseGet(() -> persistir(PartePlanta.builder()
                        .nombre("Parte sembrada " + SECUENCIA.incrementAndGet())
                        .activo(true)
                        .build()));
    }

    private Habitat habitat() {
        return primero("select h from Habitat h order by h.idHabitat", Habitat.class)
                .orElseGet(() -> persistir(Habitat.builder()
                        .nombre("Hábitat sembrado " + SECUENCIA.incrementAndGet())
                        .activo(true)
                        .build()));
    }

    private Nutriente nutriente() {
        return primero("select n from Nutriente n order by n.idNutriente", Nutriente.class)
                .orElseGet(() -> persistir(Nutriente.builder()
                        .nombre("Nutriente sembrado " + SECUENCIA.incrementAndGet())
                        .categoria(CategoriaNutriente.OTRO)
                        .activo(true)
                        .build()));
    }

    private Beneficio beneficio() {
        return primero("select b from Beneficio b order by b.idBeneficio", Beneficio.class)
                .orElseGet(() -> persistir(Beneficio.builder()
                        .nombre("Beneficio sembrado " + SECUENCIA.incrementAndGet())
                        .tipoBeneficio(TipoBeneficio.OTRO)
                        .activo(true)
                        .build()));
    }

    /**
     * Se reutiliza una fuente existente a proposito: crearla choca con
     * {@code uk_fuente_titulo_lower} si otra siembra ya puso ese titulo, y el
     * titulo no es lo que estas pruebas miran.
     */
    private Fuente fuente() {
        return primero("select f from Fuente f order by f.idFuente", Fuente.class)
                .orElseGet(() -> persistir(Fuente.builder()
                        .titulo("Fuente sembrada " + SECUENCIA.incrementAndGet())
                        .tipoFuente(TipoFuente.OTRO)
                        .activa(true)
                        .build()));
    }

    private <T> Optional<T> primero(String jpql, Class<T> tipo) {
        return em.createQuery(jpql, tipo)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    private <T> T persistir(T entidad) {
        em.persist(entidad);
        return entidad;
    }
}
