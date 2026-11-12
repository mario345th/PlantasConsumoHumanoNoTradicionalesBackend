package sv.edu.ues.fmp.flora.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import sv.edu.ues.fmp.flora.config.SiembraDeFichas;
import sv.edu.ues.fmp.flora.config.SiembraDeFichas.Ficha;
import sv.edu.ues.fmp.flora.dto.response.UsuarioResponse;
import sv.edu.ues.fmp.flora.entity.Especie;
import sv.edu.ues.fmp.flora.entity.Taxonomia;
import sv.edu.ues.fmp.flora.entity.Usuario;
import sv.edu.ues.fmp.flora.entity.enums.EstadoPublicacion;
import sv.edu.ues.fmp.flora.repository.EspecieRepository;

/**
 * Red de regresion de lo que el modulo de especie hace con una entrada sucia:
 * recortar los espacios de la clasificacion y rechazar una lista de nombres
 * comunes con un elemento nulo.
 *
 * <h2>Los dos hallazgos</h2>
 * <ul>
 *   <li><strong>Espacios en la taxonomia.</strong> Un {@code " Plantae "} con
 *       espacios alrededor reventaba con 500: los ocho campos de la
 *       clasificacion se guardaban tal cual y la comparacion contra
 *       {@code uk_taxonomia} dejaba de reconocer su propia fila. Ahora
 *       {@code EspecieServiceImpl.normalizar(TaxonomiaRequest)} los limpia, y
 *       --esto es lo que mas importa-- lo hace en los <strong>tres</strong>
 *       caminos de escritura: {@code POST /api/especies}, el {@code PUT} de la
 *       ficha y el {@code PATCH .../taxonomia}. Si alguien agregara un cuarto
 *       sin normalizar, volveria el 500.</li>
 *   <li><strong>Elemento nulo en nombresComunes.</strong> {@code @Valid} sobre
 *       la lista validaba los elementos presentes, pero un
 *       {@code "nombresComunes": [null]} no incumplia ninguna regla: pasaba la
 *       validacion entera y reventaba en el servicio al leerle
 *       {@code esPrincipal} a un null. Un 500 por un cuerpo mal formado. La
 *       restriccion pasó al ELEMENTO de la lista, de modo que ese cuerpo se
 *       queda en el 400 de Bean Validation.</li>
 * </ul>
 * Las dos correcciones se quedaron sin prueba, que es justo lo que hace que un
 * arreglo asi se deshaga sin que nadie lo note.
 *
 * <h2>Datos</h2>
 * El usuario responsable y la ficha del PATCH se siembran aqui, en la
 * transaccion de la prueba, y el rollback de {@code @Transactional} se los
 * lleva. Los nombres llevan el prefijo {@code Zztrim} para no chocar con el
 * catalogo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EspecieEntradaSaneadaTest {

    private static final String ESPECIE_CREAR = "ESPECIE_CREAR";
    private static final String ESPECIE_EDITAR = "ESPECIE_EDITAR";

    private static final AtomicLong SECUENCIA = new AtomicLong();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EspecieRepository especieRepository;

    @MockitoBean
    private EmailService emailService;

    private Usuario responsable;

    @BeforeEach
    void usuario() {
        long n = SECUENCIA.incrementAndGet();

        responsable = Usuario.builder()
                .nombres("Entrada")
                .apellidos("Saneada")
                .correo("entrada.saneada." + n + "@prueba.local")
                .nombreUsuario("entrada.saneada." + n)
                .claveHash("$2a$10$pruebaDeEntradaSaneadaSinClaveUtilizable00000000000000")
                .activo(true)
                .build();

        entityManager.persist(responsable);
        entityManager.flush();
    }

    /** Token cuyo {@code sub} es el usuario sembrado: el servicio lo exige real. */
    private String token(String... permisos) {
        UsuarioResponse usuario = UsuarioResponse.builder()
                .id(responsable.getIdUsuario())
                .nombres("Prueba")
                .apellidos("EntradaSaneada")
                .correo("prueba.entrada@local")
                .nombreUsuario("prueba.entrada")
                .activo(true)
                .build();

        return "Bearer " + jwtService
                .emitir(usuario, List.of("CONSULTOR"), List.of(permisos))
                .token();
    }

    // ---------- 1. Los espacios de la taxonomia ----------

    /**
     * Los ocho campos llegan con espacios alrededor. Se comprueba la respuesta
     * y tambien la fila, porque el 500 de antes venia de lo que se guardaba.
     */
    @Test
    void crear_conEspaciosEnLaTaxonomia_losRecortaYNoFalla() throws Exception {
        long n = SECUENCIA.incrementAndGet();

        String cuerpo = """
                {
                  "nombreCientifico": "Zztrim%d especie",
                  "descripcion": "Ficha de prueba del recorte de espacios.",
                  "taxonomia": {
                    "reino": " Plantae ",
                    "division": "  Magnoliophyta  ",
                    "clase": " Magnoliopsida ",
                    "ordenTaxonomico": "  Rosales  ",
                    "familia": " Pruebaceae ",
                    "genero": "  Zztrim%d  ",
                    "especieTaxonomica": "  espacios  ",
                    "subespecie": " recortada "
                  }
                }
                """.formatted(n, n);

        String respuesta = mockMvc.perform(post("/api/especies")
                        .header(HttpHeaders.AUTHORIZATION, token(ESPECIE_CREAR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taxonomia.reino").value("Plantae"))
                .andExpect(jsonPath("$.taxonomia.division").value("Magnoliophyta"))
                .andExpect(jsonPath("$.taxonomia.clase").value("Magnoliopsida"))
                .andExpect(jsonPath("$.taxonomia.ordenTaxonomico").value("Rosales"))
                .andExpect(jsonPath("$.taxonomia.familia").value("Pruebaceae"))
                .andExpect(jsonPath("$.taxonomia.genero").value("Zztrim" + n))
                .andExpect(jsonPath("$.taxonomia.especieTaxonomica").value("espacios"))
                .andExpect(jsonPath("$.taxonomia.subespecie").value("recortada"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long idEspecie = ((Number) JsonPath.read(respuesta, "$.idEspecie")).longValue();
        Taxonomia guardada = especieRepository.findById(idEspecie).orElseThrow().getTaxonomia();

        assertEquals("Plantae", guardada.getReino());
        assertEquals("Zztrim" + n, guardada.getGenero());
        assertEquals("espacios", guardada.getEspecieTaxonomica());
        assertEquals("recortada", guardada.getSubespecie());
        assertEquals("Pruebaceae", guardada.getFamilia());
    }

    /**
     * El segundo camino de escritura. Es el que mas importa de los dos: es el
     * que se agrego despues, y por el que se cuela un campo sin normalizar si
     * alguien lo olvida.
     */
    @Test
    void actualizarTaxonomia_conEspacios_losRecortaYNoFalla() throws Exception {
        long n = SECUENCIA.incrementAndGet();

        Ficha ficha = new SiembraDeFichas(entityManager).sembrar(EstadoPublicacion.BORRADOR);

        String cuerpo = """
                {
                  "reino": " Plantae ",
                  "familia": "  Pruebaceae  ",
                  "genero": "  Zztrimpatch%d  ",
                  "especieTaxonomica": "  parcheada  ",
                  "subespecie": "  sub  "
                }
                """.formatted(n);

        mockMvc.perform(patch("/api/especies/" + ficha.id() + "/taxonomia")
                        .header(HttpHeaders.AUTHORIZATION, token(ESPECIE_EDITAR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxonomia.reino").value("Plantae"))
                .andExpect(jsonPath("$.taxonomia.familia").value("Pruebaceae"))
                .andExpect(jsonPath("$.taxonomia.genero").value("Zztrimpatch" + n))
                .andExpect(jsonPath("$.taxonomia.especieTaxonomica").value("parcheada"))
                .andExpect(jsonPath("$.taxonomia.subespecie").value("sub"));

        entityManager.flush();

        Taxonomia guardada = especieRepository.findById(ficha.id()).orElseThrow().getTaxonomia();

        assertEquals("Zztrimpatch" + n, guardada.getGenero());
        assertEquals("parcheada", guardada.getEspecieTaxonomica());
        assertEquals("sub", guardada.getSubespecie());
    }

    /**
     * El tercer camino: el {@code PUT} de la ficha completa. Se incluye para
     * que los tres queden fijados, que es la forma de que un cuarto que nazca
     * sin normalizar se note.
     */
    @Test
    void actualizarLaFicha_conEspaciosEnLaTaxonomia_losRecorta() throws Exception {
        long n = SECUENCIA.incrementAndGet();

        Ficha ficha = new SiembraDeFichas(entityManager).sembrar(EstadoPublicacion.BORRADOR);

        String cuerpo = """
                {
                  "nombreCientifico": "Zztrimput%d especie",
                  "descripcion": "Ficha de prueba del recorte por PUT.",
                  "taxonomia": {
                    "reino": " Plantae ",
                    "genero": "  Zztrimput%d  ",
                    "especieTaxonomica": "  puesta  "
                  }
                }
                """.formatted(n, n);

        mockMvc.perform(put("/api/especies/" + ficha.id())
                        .header(HttpHeaders.AUTHORIZATION, token(ESPECIE_EDITAR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxonomia.genero").value("Zztrimput" + n))
                .andExpect(jsonPath("$.taxonomia.especieTaxonomica").value("puesta"));

        entityManager.flush();

        Taxonomia guardada = especieRepository.findById(ficha.id()).orElseThrow().getTaxonomia();

        assertEquals("Zztrimput" + n, guardada.getGenero());
        assertEquals("puesta", guardada.getEspecieTaxonomica());
    }

    // ---------- 2. El elemento nulo en nombresComunes ----------

    @Test
    void crear_conUnElementoNuloEnNombresComunes_devuelve400YNo500() throws Exception {
        long n = SECUENCIA.incrementAndGet();

        String cuerpo = """
                {
                  "nombreCientifico": "Zztrimnulo%d especie",
                  "descripcion": "Ficha de prueba del elemento nulo.",
                  "taxonomia": { "genero": "Zztrimnulo%d", "especieTaxonomica": "nulo" },
                  "nombresComunes": [null]
                }
                """.formatted(n, n);

        mockMvc.perform(post("/api/especies")
                        .header(HttpHeaders.AUTHORIZATION, token(ESPECIE_CREAR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400))
                .andExpect(jsonPath("$.error").value("Error de validación"))
                .andExpect(jsonPath("$.erroresValidacion.*")
                        .value(Matchers.hasItem(Matchers.containsString(
                                "no se admiten elementos nulos"))));

        assertEquals(0, cuantasSeLlaman("Zztrimnulo" + n + " especie"),
                "el 400 dejo la especie creada");
    }

    /**
     * El elemento nulo acompanado de uno valido tambien se rechaza: lo que
     * falla es la lista, no que no haya nada bueno en ella.
     */
    @Test
    void crear_conUnElementoNuloJuntoAUnoValido_devuelve400() throws Exception {
        long n = SECUENCIA.incrementAndGet();

        String cuerpo = """
                {
                  "nombreCientifico": "Zztrimmixto%d especie",
                  "descripcion": "Ficha de prueba del elemento nulo acompañado.",
                  "taxonomia": { "genero": "Zztrimmixto%d", "especieTaxonomica": "mixto" },
                  "nombresComunes": [
                    { "nombre": "Nombre válido", "region": "Prueba", "esPrincipal": true },
                    null
                  ]
                }
                """.formatted(n, n);

        mockMvc.perform(post("/api/especies")
                        .header(HttpHeaders.AUTHORIZATION, token(ESPECIE_CREAR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400));

        assertEquals(0, cuantasSeLlaman("Zztrimmixto" + n + " especie"),
                "el 400 dejo la especie creada");
    }

    /** La misma lista sin el nulo si crea la ficha: el 400 era por el nulo. */
    @Test
    void crear_conLaListaSinNulos_devuelve201() throws Exception {
        long n = SECUENCIA.incrementAndGet();

        String cuerpo = """
                {
                  "nombreCientifico": "Zztrimbueno%d especie",
                  "descripcion": "Ficha de prueba de la lista sin nulos.",
                  "taxonomia": { "genero": "Zztrimbueno%d", "especieTaxonomica": "bueno" },
                  "nombresComunes": [
                    { "nombre": "Nombre válido", "region": "Prueba", "esPrincipal": true }
                  ]
                }
                """.formatted(n, n);

        mockMvc.perform(post("/api/especies")
                        .header(HttpHeaders.AUTHORIZATION, token(ESPECIE_CREAR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombresComunes[0].nombre").value("Nombre válido"))
                .andExpect(jsonPath("$.nombresComunes[0].esPrincipal").value(true));

        assertEquals(1, cuantasSeLlaman("Zztrimbueno" + n + " especie"));
    }

    private long cuantasSeLlaman(String nombreCientifico) {
        entityManager.flush();

        return especieRepository.findAll().stream()
                .map(Especie::getNombreCientifico)
                .filter(nombre -> nombre.equalsIgnoreCase(nombreCientifico))
                .count();
    }
}
