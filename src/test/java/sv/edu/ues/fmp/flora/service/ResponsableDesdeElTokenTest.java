package sv.edu.ues.fmp.flora.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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
import sv.edu.ues.fmp.flora.entity.Usuario;
import sv.edu.ues.fmp.flora.entity.enums.EstadoPublicacion;
import sv.edu.ues.fmp.flora.repository.EspecieRepository;

/**
 * Red de regresion del arreglo de trazabilidad: que {@code creada_por},
 * {@code validada_por} y {@code publicada_por} salgan del token y no de lo que
 * mande el cliente.
 *
 * <h2>El agujero que cerro el arreglo</h2>
 * El flujo editorial tomaba al responsable de donde lo dijera quien llamaba:
 * {@code creadaPor} venia en el cuerpo del POST y {@code idValidador} /
 * {@code idPublicador} en la query de los PATCH. Cualquiera con el permiso
 * correspondiente podia firmar una ficha en nombre de otro usuario --de un
 * administrador, por ejemplo-- y las tres columnas de la tabla {@code especie}
 * no valian como registro de quien hizo que. Ahora el id sale de
 * {@link UsuarioAutenticado}, que lee el {@code sub} del token y no acepta
 * ningun dato del cliente.
 *
 * <h2>Por que las pruebas siguen mandando el id viejo</h2>
 * Los campos desaparecieron del DTO y los parametros del controlador, asi que
 * hoy son datos que nadie lee. Mandarlos igual es el punto: lo que se fija aqui
 * es que un cliente que siga enviandolos --porque quedo escrito en un front, o
 * porque alguien lo intenta a proposito-- no cambie la atribucion. Jackson
 * ignora las propiedades que no conoce y Spring los parametros de query que
 * nadie declara, de modo que la peticion entra igual y la unica diferencia
 * posible esta en quien queda firmando.
 *
 * <h2>La prueba que distingue</h2>
 * {@link #crear_conTokenDeUnUsuarioInexistente_falla404AunqueElCuerpoTraigaUnoValido()}
 * es la que no se puede pasar por casualidad: el token identifica a un usuario
 * que no existe y el cuerpo trae uno que si. Con el arreglo la creacion falla
 * con 404 porque el unico id que se mira es el del token; con el
 * comportamiento viejo la ficha se habria creado a nombre del usuario del
 * cuerpo y la respuesta habria sido 201. Es el equivalente de lo que se hizo
 * con MM-106, pero sin tener que desarmar el arreglo para comprobarlo.
 *
 * <h2>Datos</h2>
 * Los dos usuarios y la ficha EN_REVISION se crean aqui dentro, en la
 * transaccion de la prueba, y el rollback de {@code @Transactional} se los
 * lleva: no queda residuo en la base.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ResponsableDesdeElTokenTest {

    private static final String ESPECIE_CREAR = "ESPECIE_CREAR";
    private static final String ESPECIE_VALIDAR = "ESPECIE_VALIDAR";
    private static final String ESPECIE_PUBLICAR = "ESPECIE_PUBLICAR";

    /** Un id de usuario que no existe en ninguna base. */
    private static final long ID_USUARIO_INEXISTENTE = 999999L;

    /** Para que los nombres cientificos y los generos no choquen entre si. */
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

    /** El del token: el que tiene que quedar firmando. */
    private Usuario quienLlama;

    /** El que viaja en el cuerpo o en la query: el que NO debe quedar firmando. */
    private Usuario elOtro;

    @BeforeEach
    void usuarios() {
        quienLlama = crearUsuario("Quien", "Llama");
        elOtro = crearUsuario("El", "Otro");
        entityManager.flush();
    }

    private Usuario crearUsuario(String nombres, String apellidos) {
        long n = SECUENCIA.incrementAndGet();

        Usuario usuario = Usuario.builder()
                .nombres(nombres)
                .apellidos(apellidos)
                .correo("responsable.prueba." + n + "@prueba.local")
                .nombreUsuario("responsable.prueba." + n)
                // No es una clave usable: estos usuarios no inician sesion, el
                // token se emite directamente con JwtService.
                .claveHash("$2a$10$pruebaDeAtribucionSinClaveUtilizable0000000000000000000")
                .activo(true)
                .build();

        entityManager.persist(usuario);
        return usuario;
    }

    /** Token cuyo {@code sub} es el id que se le pase. */
    private String tokenDe(Long idUsuario, String... permisos) {
        UsuarioResponse usuario = UsuarioResponse.builder()
                .id(idUsuario)
                .nombres("Prueba")
                .apellidos("Atribucion")
                .correo("prueba.atribucion." + idUsuario + "@local")
                .nombreUsuario("prueba.atribucion." + idUsuario)
                .activo(true)
                .build();

        return "Bearer " + jwtService.emitir(usuario, List.of("CONSULTOR"), List.of(permisos)).token();
    }

    private static String nombreCompleto(Usuario usuario) {
        return usuario.getNombres() + " " + usuario.getApellidos();
    }

    /**
     * Cuerpo valido para el POST, con el {@code creadaPor} que el DTO ya no
     * tiene. Jackson lo descarta; si alguien lo volviera a declarar y a usar,
     * esta prueba se cae.
     */
    private static String cuerpoConCreadaPor(long idResponsable, long n) {
        return """
                {
                  "nombreCientifico": "Zzatribucion%d especie",
                  "descripcion": "Ficha de prueba de atribución de responsables.",
                  "taxonomia": { "genero": "Zzatribucion%d", "especieTaxonomica": "atribucion" },
                  "creadaPor": %d
                }
                """.formatted(n, n, idResponsable);
    }

    // ---------- crear ----------

    @Test
    void crear_conOtroResponsableEnElCuerpo_atribuyeLaFichaAlUsuarioDelToken() throws Exception {
        long n = SECUENCIA.incrementAndGet();

        String respuesta = mockMvc.perform(post("/api/especies")
                        .header(HttpHeaders.AUTHORIZATION,
                                tokenDe(quienLlama.getIdUsuario(), ESPECIE_CREAR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoConCreadaPor(elOtro.getIdUsuario(), n)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creadaPor").value(nombreCompleto(quienLlama)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Y en la tabla, no solo en la respuesta.
        Long idEspecie = ((Number) JsonPath.read(respuesta, "$.idEspecie")).longValue();
        Especie guardada = especieRepository.findById(idEspecie).orElseThrow();

        assertEquals(quienLlama.getIdUsuario(), guardada.getCreadaPor().getIdUsuario(),
                "la ficha quedo atribuida a quien dijo el cuerpo, no a quien trae el token");
        assertNotEquals(elOtro.getIdUsuario(), guardada.getCreadaPor().getIdUsuario());
    }

    /**
     * La prueba que no pasa por casualidad: el {@code sub} del token apunta a
     * un usuario que no existe y el cuerpo trae uno que si. Si el servicio
     * mirara el cuerpo, esto seria un 201.
     */
    @Test
    void crear_conTokenDeUnUsuarioInexistente_falla404AunqueElCuerpoTraigaUnoValido() throws Exception {
        long n = SECUENCIA.incrementAndGet();

        mockMvc.perform(post("/api/especies")
                        .header(HttpHeaders.AUTHORIZATION,
                                tokenDe(ID_USUARIO_INEXISTENTE, ESPECIE_CREAR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoConCreadaPor(elOtro.getIdUsuario(), n)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje")
                        .value("No existe el usuario creador con id " + ID_USUARIO_INEXISTENTE));

        // Y no dejo la ficha a medias: el 404 sale antes de guardar nada.
        assertEquals(0,
                especieRepository.findAll().stream()
                        .filter(especie -> especie.getNombreCientifico()
                                .equalsIgnoreCase("Zzatribucion" + n + " especie"))
                        .count(),
                "el 404 dejo la especie creada");
    }

    // ---------- validar y publicar ----------

    @Test
    void validar_conOtroValidadorEnLaQuery_atribuyeLaValidacionAlUsuarioDelToken() throws Exception {
        Ficha ficha = new SiembraDeFichas(entityManager).sembrar(EstadoPublicacion.EN_REVISION);

        mockMvc.perform(patch("/api/especies/" + ficha.id() + "/validar")
                        // El parametro que el controlador ya no declara.
                        .param("idValidador", String.valueOf(elOtro.getIdUsuario()))
                        .header(HttpHeaders.AUTHORIZATION,
                                tokenDe(quienLlama.getIdUsuario(), ESPECIE_VALIDAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validadaPor").value(nombreCompleto(quienLlama)));

        Especie guardada = especieRepository.findById(ficha.id()).orElseThrow();

        assertEquals(quienLlama.getIdUsuario(), guardada.getValidadaPor().getIdUsuario(),
                "la validacion quedo atribuida al idValidador de la query");
    }

    @Test
    void publicar_conOtroPublicadorEnLaQuery_atribuyeLaPublicacionAlUsuarioDelToken()
            throws Exception {

        Ficha ficha = new SiembraDeFichas(entityManager).sembrar(EstadoPublicacion.EN_REVISION);

        // Publicar exige una ficha ya validada; se valida con el mismo token.
        mockMvc.perform(patch("/api/especies/" + ficha.id() + "/validar")
                        .header(HttpHeaders.AUTHORIZATION,
                                tokenDe(quienLlama.getIdUsuario(), ESPECIE_VALIDAR)))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/especies/" + ficha.id() + "/publicar")
                        .param("idPublicador", String.valueOf(elOtro.getIdUsuario()))
                        .header(HttpHeaders.AUTHORIZATION,
                                tokenDe(quienLlama.getIdUsuario(), ESPECIE_PUBLICAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPublicacion").value("PUBLICADA"))
                .andExpect(jsonPath("$.publicadaPor").value(nombreCompleto(quienLlama)));

        Especie guardada = especieRepository.findById(ficha.id()).orElseThrow();

        assertEquals(quienLlama.getIdUsuario(), guardada.getPublicadaPor().getIdUsuario(),
                "la publicacion quedo atribuida al idPublicador de la query");
        assertEquals(quienLlama.getIdUsuario(), guardada.getValidadaPor().getIdUsuario());
    }

    /**
     * El mismo criterio en el otro extremo: un token sin {@code sub} usable no
     * firma nada. Es la rama que {@link UsuarioAutenticado} documenta y que
     * ningun cliente deberia alcanzar, pero si el dia que alguien emita tokens
     * con el nombre de usuario en el {@code sub} el flujo editorial empieza a
     * guardar fichas sin responsable, esto lo caza antes.
     */
    @Test
    void validar_conUnTokenCuyoSubNoEsUnId_noAtribuyeNada() throws Exception {
        Ficha ficha = new SiembraDeFichas(entityManager).sembrar(EstadoPublicacion.EN_REVISION);

        UsuarioResponse conSubDeTexto = UsuarioResponse.builder()
                .id(null)
                .nombres("Sin")
                .apellidos("Id")
                .correo("sin.id@local")
                .nombreUsuario("sin.id")
                .activo(true)
                .build();

        String token = jwtService
                .emitir(conSubDeTexto, List.of("CONSULTOR"), List.of(ESPECIE_VALIDAR))
                .token();

        mockMvc.perform(patch("/api/especies/" + ficha.id() + "/validar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        Especie sinTocar = especieRepository.findById(ficha.id()).orElseThrow();
        assertEquals(null, sinTocar.getValidadaPor(), "quedo validada con un token sin id");
    }
}
