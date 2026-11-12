package sv.edu.ues.fmp.flora.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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

import sv.edu.ues.fmp.flora.dto.response.UsuarioResponse;
import sv.edu.ues.fmp.flora.repository.FuenteRepository;

/**
 * Red de regresion de la unicidad del titulo de {@code fuente}: que el segundo
 * intento sea un 409 legible y no un 500 ni una fila repetida.
 *
 * <h2>Lo que estaba mal</h2>
 * QA encontro la misma fuente cargada varias veces con el titulo escrito
 * distinto (mayusculas, espacios de sobra), y el modulo no tenia nada que lo
 * impidiera: {@code fuente} solo traia su llave primaria. La correccion tiene
 * dos mitades y esta clase prueba las dos:
 * <ul>
 *   <li>La migracion {@code V18} limpio los duplicados y creo el indice unico
 *       funcional {@code uk_fuente_titulo_lower} sobre
 *       {@code lower(btrim(titulo))}.</li>
 *   <li>{@code FuenteServiceImpl} normaliza el titulo --recorta los extremos y
 *       colapsa los espacios internos-- comprueba antes de insertar y, si de
 *       todas formas choca con el indice, traduce el
 *       {@code DataIntegrityViolationException} a un 409 con mensaje propio en
 *       vez de dejarlo salir como 500.</li>
 * </ul>
 *
 * <h2>Por que las variantes</h2>
 * Un 409 contra el titulo identico no prueba gran cosa: lo que QA vio fue el
 * mismo titulo con otra caja y con espacios de mas. Las tres variantes
 * --identico, otra caja, espacios alrededor y dobles por dentro-- son las que
 * de verdad fijan el comportamiento, porque cada una pasa por una pieza
 * distinta: la comparacion {@code IgnoreCase} del repositorio y la
 * normalizacion del servicio.
 *
 * <h2>Datos</h2>
 * Las fuentes se crean por la propia API dentro de la transaccion de la prueba
 * y el rollback de {@code @Transactional} se las lleva. El prefijo
 * {@code Zzfuente} evita cualquier coincidencia con los titulos del catalogo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FuenteTituloDuplicadoTest {

    private static final String FUENTE_GESTIONAR = "FUENTE_GESTIONAR";

    private static final String TITULO = "Zzfuente de prueba duplicada";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private FuenteRepository fuenteRepository;

    @MockitoBean
    private EmailService emailService;

    private String token() {
        UsuarioResponse usuario = UsuarioResponse.builder()
                .id(999L)
                .nombres("Prueba")
                .apellidos("Fuente")
                .correo("prueba.fuente@local")
                .nombreUsuario("prueba.fuente")
                .activo(true)
                .build();

        return "Bearer " + jwtService
                .emitir(usuario, List.of("CONSULTOR"), List.of(FUENTE_GESTIONAR))
                .token();
    }

    private static String cuerpo(String titulo) {
        return """
                {
                  "titulo": "%s",
                  "autor": "Pruebas automáticas",
                  "tipoFuente": "OTRO"
                }
                """.formatted(titulo);
    }

    /** Crea la primera fuente y devuelve su id. */
    private Long crearLaPrimera() throws Exception {
        String respuesta = mockMvc.perform(post("/api/fuentes")
                        .header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(TITULO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titulo").value(TITULO))
                .andReturn()
                .getResponse()
                .getContentAsString();

        return ((Number) JsonPath.read(respuesta, "$.idFuente")).longValue();
    }

    /**
     * Segundo intento con la variante que se le pase: 409, con el mensaje del
     * servicio --no el del driver-- y sin que quede una segunda fila.
     */
    private void segundoIntentoChoca(String variante) throws Exception {
        crearLaPrimera();

        mockMvc.perform(post("/api/fuentes")
                        .header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(variante)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.estado").value(409))
                .andExpect(jsonPath("$.error").value("Conflicto"))
                .andExpect(jsonPath("$.mensaje")
                        .value(org.hamcrest.Matchers.startsWith("Ya existe una fuente con el título")));

        assertEquals(1, fuenteRepository.findAll().stream()
                        .filter(fuente -> fuente.getTitulo().equalsIgnoreCase(TITULO))
                        .count(),
                "quedo mas de una fuente con el mismo título");
    }

    @Test
    void crear_elMismoTituloDosVeces_devuelve409() throws Exception {
        segundoIntentoChoca(TITULO);
    }

    @Test
    void crear_elMismoTituloConOtrasMayusculas_devuelve409() throws Exception {
        segundoIntentoChoca("ZZFUENTE DE PRUEBA DUPLICADA");
    }

    /**
     * Espacios alrededor y uno doble por dentro: es la variante que depende de
     * la normalizacion del servicio, porque el indice de la base
     * --{@code lower(btrim(titulo))}-- recorta los extremos pero no colapsa lo
     * de en medio.
     */
    @Test
    void crear_elMismoTituloConEspaciosDeSobra_devuelve409() throws Exception {
        segundoIntentoChoca("   Zzfuente de  prueba   duplicada  ");
    }

    /** Y lo que se guarda es el titulo ya normalizado, no el que llego. */
    @Test
    void crear_guardaElTituloSinEspaciosDeSobra() throws Exception {
        String respuesta = mockMvc.perform(post("/api/fuentes")
                        .header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("   Zzfuente de  prueba   duplicada  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titulo").value(TITULO))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long id = ((Number) JsonPath.read(respuesta, "$.idFuente")).longValue();

        assertEquals(TITULO, fuenteRepository.findById(id).orElseThrow().getTitulo(),
                "el título se guardó con los espacios tal como llegaron");
    }

    /**
     * El PUT tiene su propio mensaje ("otra fuente") y su propia excepcion: la
     * comprobacion excluye a la fila que se esta editando, asi que guardar una
     * fuente sin cambiarle el titulo no puede ser un conflicto.
     */
    @Test
    void actualizar_haciaElTituloDeOtraFuente_devuelve409() throws Exception {
        crearLaPrimera();

        String respuesta = mockMvc.perform(post("/api/fuentes")
                        .header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("Zzfuente de prueba distinta")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long idDeLaSegunda = ((Number) JsonPath.read(respuesta, "$.idFuente")).longValue();

        mockMvc.perform(put("/api/fuentes/" + idDeLaSegunda)
                        .header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("zzfuente DE prueba DUPLICADA")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.estado").value(409))
                .andExpect(jsonPath("$.mensaje")
                        .value(org.hamcrest.Matchers.startsWith("Ya existe otra fuente con el título")));

        // Y la segunda conserva su titulo: el 409 no dejo el UPDATE a medias.
        assertEquals("Zzfuente de prueba distinta",
                fuenteRepository.findById(idDeLaSegunda).orElseThrow().getTitulo());
    }

    /** Guardar una fuente sin cambiarle el titulo no es un conflicto. */
    @Test
    void actualizar_conservandoSuPropioTitulo_devuelve200() throws Exception {
        Long id = crearLaPrimera();

        mockMvc.perform(put("/api/fuentes/" + id)
                        .header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(TITULO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value(TITULO));
    }
}
