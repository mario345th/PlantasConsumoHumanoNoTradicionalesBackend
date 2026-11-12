package sv.edu.ues.fmp.flora.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import sv.edu.ues.fmp.flora.config.SiembraDeFichas.Ficha;
import sv.edu.ues.fmp.flora.dto.response.UsuarioResponse;
import sv.edu.ues.fmp.flora.entity.enums.EstadoPublicacion;
import sv.edu.ues.fmp.flora.service.EmailService;
import sv.edu.ues.fmp.flora.service.JwtService;

/**
 * Pruebas de la fuga de borradores: que un anonimo no pueda ver una ficha que
 * no esta PUBLICADA, por ninguna de las tres vias por las que podia.
 *
 * <h2>Lo que estaba mal</h2>
 * {@code /api/especies/**} entero estaba en {@code CATALOGO_PUBLICO}, asi que
 * sin token se podia llamar a {@code GET /api/especies} (un {@code findAll()}
 * por dentro), a {@code GET /api/especies?estado=BORRADOR} y a
 * {@code GET /api/especies/{id}} de una ficha en preparacion.
 *
 * <h2>Por que ahora siembra en vez de buscar en la base</h2>
 * Esta clase buscaba una ficha sin publicar entre los datos de las migraciones
 * y, si no la habia, se saltaba con {@code Assumptions.assumeTrue}. Cuando
 * {@code V14} recargo el catalogo dejando las quince fichas en PUBLICADA, cinco
 * pruebas de aqui dejaron de ejecutarse sin que nada fallara. Ahora
 * {@link SiembraDeFichas} crea en el {@code @BeforeEach} una ficha BORRADOR,
 * una EN_REVISION y una PUBLICADA --con su arbol de hijos-- y las pruebas
 * trabajan sobre esas: corren siempre, con los datos que sean, y fallan si el
 * guard se rompe.
 * <p>
 * La siembra incluye los dos estados sin publicar a proposito. El hallazgo
 * original hablaba de BORRADOR y EN_REVISION, y lo que cierra la puerta no es
 * el nombre del estado sino la condicion {@code estado = PUBLICADA}; probar los
 * dos es lo que demuestra que no se dejo uno fuera.
 * <p>
 * Todo lo sembrado vive en la transaccion de la prueba y el rollback de
 * {@code @Transactional} se lo lleva: la base queda como estaba.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EspecieVisibilidadPublicaTest {

    private static final String ESPECIE_CONSULTAR = "ESPECIE_CONSULTAR";

    private static final String LISTADO_INTERNO = "/api/especies";
    private static final String LISTADO_PUBLICO = "/api/especies/publicadas";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private EmailService emailService;

    private Ficha borrador;
    private Ficha enRevision;
    private Ficha publicada;

    @BeforeEach
    void sembrar() {
        SiembraDeFichas siembra = new SiembraDeFichas(entityManager);

        borrador = siembra.sembrar(EstadoPublicacion.BORRADOR);
        enRevision = siembra.sembrar(EstadoPublicacion.EN_REVISION);
        publicada = siembra.sembrar(EstadoPublicacion.PUBLICADA);
    }

    /** Las dos fichas que el anonimo no debe ver por ninguna via. */
    private List<Ficha> sinPublicar() {
        return List.of(borrador, enRevision);
    }

    private String tokenCon(String... permisos) {
        UsuarioResponse usuario = UsuarioResponse.builder()
                .id(999L)
                .nombres("Prueba")
                .apellidos("Visibilidad")
                .correo("prueba.visibilidad@local")
                .nombreUsuario("prueba.visibilidad")
                .activo(true)
                .build();

        return "Bearer " + jwtService.emitir(usuario, List.of("CONSULTOR"), List.of(permisos)).token();
    }

    // ---------- El anonimo no llega a los borradores ----------

    @Test
    void listadoInterno_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get(LISTADO_INTERNO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.estado").value(401));
    }

    @Test
    void filtroPorEstadoBorrador_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get(LISTADO_INTERNO).param("estado", "BORRADOR"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(LISTADO_INTERNO).param("estado", "EN_REVISION"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void detalleInternoDeUnBorrador_sinToken_devuelve401() throws Exception {
        for (Ficha ficha : sinPublicar()) {
            mockMvc.perform(get(LISTADO_INTERNO + "/" + ficha.id()))
                    .andExpect(status().isUnauthorized());
        }
    }

    /**
     * Por el detalle publico la ficha sin publicar no es 401 ni 403 sino 404,
     * con el mismo mensaje que un id que no existe. Si respondiera distinto,
     * probando numeros se podria averiguar que fichas hay en preparacion.
     */
    @Test
    void detallePublicoDeUnBorrador_devuelve404ComoSiNoExistiera() throws Exception {
        for (Ficha ficha : sinPublicar()) {
            mockMvc.perform(get(LISTADO_PUBLICO + "/" + ficha.id()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.mensaje")
                            .value("No existe una especie con id " + ficha.id()));
        }

        mockMvc.perform(get(LISTADO_PUBLICO + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No existe una especie con id 999999"));
    }

    @Test
    void listadoPublico_noIncluyeLasFichasSinPublicar() throws Exception {
        mockMvc.perform(get(LISTADO_PUBLICO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idEspecie")
                        .value(Matchers.not(Matchers.hasItem(borrador.id().intValue()))))
                .andExpect(jsonPath("$[*].idEspecie")
                        .value(Matchers.not(Matchers.hasItem(enRevision.id().intValue()))))
                // Y la sembrada PUBLICADA si esta: que el listado no filtre de
                // mas, que seria la otra forma de pasar esta prueba.
                .andExpect(jsonPath("$[*].idEspecie")
                        .value(Matchers.hasItem(publicada.id().intValue())))
                .andExpect(jsonPath("$[*].estadoPublicacion")
                        .value(Matchers.everyItem(Matchers.is("PUBLICADA"))));
    }

    // ---------- ESPECIE_CONSULTAR por fin sirve para algo ----------

    @Test
    void listadoInterno_conEspecieConsultar_devuelve200() throws Exception {
        mockMvc.perform(get(LISTADO_INTERNO)
                        .header(HttpHeaders.AUTHORIZATION, tokenCon(ESPECIE_CONSULTAR)))
                .andExpect(status().isOk())
                // El listado interno si trae las que no estan publicadas: es su
                // razon de ser y lo que justifica que pida permiso.
                .andExpect(jsonPath("$[*].idEspecie")
                        .value(Matchers.hasItem(borrador.id().intValue())))
                .andExpect(jsonPath("$[*].idEspecie")
                        .value(Matchers.hasItem(enRevision.id().intValue())));
    }

    @Test
    void listadoInterno_conTokenSinEspecieConsultar_devuelve403() throws Exception {
        mockMvc.perform(get(LISTADO_INTERNO)
                        .header(HttpHeaders.AUTHORIZATION, tokenCon("MULTIMEDIA_GESTIONAR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.estado").value(403));
    }

    @Test
    void filtroPorEstado_conEspecieConsultar_sirveParaLeerBorradores() throws Exception {
        for (Ficha ficha : sinPublicar()) {
            mockMvc.perform(get(LISTADO_INTERNO)
                            .param("estado", ficha.estado().name())
                            .header(HttpHeaders.AUTHORIZATION, tokenCon(ESPECIE_CONSULTAR)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].idEspecie")
                            .value(Matchers.hasItem(ficha.id().intValue())))
                    .andExpect(jsonPath("$[*].estadoPublicacion")
                            .value(Matchers.everyItem(Matchers.is(ficha.estado().name()))));
        }
    }

    @Test
    void detalleInternoDeUnBorrador_conEspecieConsultar_devuelve200() throws Exception {
        for (Ficha ficha : sinPublicar()) {
            mockMvc.perform(get(LISTADO_INTERNO + "/" + ficha.id())
                            .header(HttpHeaders.AUTHORIZATION, tokenCon(ESPECIE_CONSULTAR)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.idEspecie").value(ficha.id()))
                    .andExpect(jsonPath("$.estadoPublicacion").value(ficha.estado().name()));
        }
    }

    // ---------- El catalogo publico no se rompio ----------

    @Test
    void listadoPublico_sigueAbiertoSinToken() throws Exception {
        mockMvc.perform(get(LISTADO_PUBLICO))
                .andExpect(status().isOk());
    }

    @Test
    void detallePublicoDeUnaPublicada_sigueAbiertoSinToken() throws Exception {
        mockMvc.perform(get(LISTADO_PUBLICO + "/" + publicada.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idEspecie").value(publicada.id()))
                .andExpect(jsonPath("$.estadoPublicacion").value("PUBLICADA"));
    }

    /**
     * Los sub-recursos de una especie PUBLICADA siguen abiertos en GET porque
     * son los que pinta la ficha publica. Lo que esta prueba fija es que el
     * cambio no los cerro; que con un borrador respondan 404 se prueba en
     * {@link EspecieSubRecursosVisibilidadTest}.
     */
    @Test
    void subRecursosDeUnaEspecie_siguenAbiertosSinToken() throws Exception {
        Long id = publicada.id();

        mockMvc.perform(get("/api/especies/" + id + "/imagenes")).andExpect(status().isOk());
        mockMvc.perform(get("/api/especies/" + id + "/videos")).andExpect(status().isOk());
        mockMvc.perform(get("/api/especies/" + id + "/nombres-comunes")).andExpect(status().isOk());
        mockMvc.perform(get("/api/especies/" + id + "/habitats")).andExpect(status().isOk());
        mockMvc.perform(get("/api/especies/" + id + "/fuentes")).andExpect(status().isOk());
        mockMvc.perform(get("/api/especies/" + id + "/partes-comestibles")).andExpect(status().isOk());
    }

    /** El resto del catalogo auxiliar no entro en este cambio. */
    @Test
    void restoDelCatalogo_sigueAbiertoSinToken() throws Exception {
        mockMvc.perform(get("/api/habitats")).andExpect(status().isOk());
        mockMvc.perform(get("/api/nutrientes")).andExpect(status().isOk());
        mockMvc.perform(get("/api/partes-planta")).andExpect(status().isOk());
    }
}
