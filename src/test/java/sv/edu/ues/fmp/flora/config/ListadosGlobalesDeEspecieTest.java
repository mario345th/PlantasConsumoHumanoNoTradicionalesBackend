package sv.edu.ues.fmp.flora.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import sv.edu.ues.fmp.flora.dto.response.UsuarioResponse;
import sv.edu.ues.fmp.flora.entity.EspecieParteComestible;
import sv.edu.ues.fmp.flora.repository.EspecieParteComestibleRepository;
import sv.edu.ues.fmp.flora.service.EmailService;
import sv.edu.ues.fmp.flora.service.JwtService;

/**
 * Pruebas de los cinco listados <strong>globales</strong> de contenido de
 * especie: los que no reciben el id de ninguna ficha.
 *
 * <h2>Lo que estaba mal</h2>
 * Cerradas las consultas que entran por el id de una especie
 * ({@link EspecieVisibilidadPublicaTest},
 * {@link EspecieSubRecursosVisibilidadTest}) y las que entran por el id de un
 * hijo suyo ({@link EspecieRutasPlanasVisibilidadTest}), quedaban los listados
 * que no entran por ningun id: un {@code findAll()} de preparaciones, dos de
 * parte-beneficio, el cruce por nutriente y el de epocas de cosecha. Ninguno
 * miraba el estado de la ficha de la que cuelga cada renglon, asi que sin token
 * devolvian tambien el contenido de los BORRADOR y EN_REVISION: justo el que
 * las rutas por padre ya habian dejado de mostrar.
 *
 * <h2>Por que aqui es 401 y no 404</h2>
 * En las rutas planas la respuesta tenia que ser 404 porque un 403 sobre el id
 * de una imagen confirmaba que esa imagen existe. Un listado no se pide por el
 * id de nada: cortarlo en el filtro no delata ninguna fila, asi que estos cinco
 * se cierran en la matriz de {@link SecurityConfig} --quedan fuera de
 * {@code CATALOGO_PUBLICO}, recortados por {@code LISTADOS_GLOBALES}-- mas un
 * {@code @PreAuthorize} en el metodo. De ahi los tres codigos que se afirman:
 * 401 sin token, 403 con token sin el permiso y 200 con
 * {@code ESPECIE_CONSULTAR}.
 *
 * <h2>Por que casi ninguna prueba necesita datos</h2>
 * Un listado contesta 200 con lista vacia, asi que no hace falta sembrar nada
 * para medir el guard. La unica excepcion es {@code GET /api/epocas-cosecha},
 * que exige el id de una parte comestible y responde 404 si no existe: esa
 * prueba busca una parte en la base y se salta cuando no hay ninguna, el mismo
 * criterio de {@link AutenticacionJwtTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
// Solo lecturas; el rollback deja constancia de que estas pruebas no tocan los
// datos sembrados que usan.
@Transactional
class ListadosGlobalesDeEspecieTest {

    private static final String ESPECIE_CONSULTAR = "ESPECIE_CONSULTAR";

    /** Un permiso real del proyecto, pero de otro modulo: sirve para el 403. */
    private static final String OTRO_PERMISO = "MULTIMEDIA_GESTIONAR";

    private static final String ID_INEXISTENTE = "999999";

    private static final String PREPARACIONES = "/api/preparaciones-consumo";
    private static final String PARTE_BENEFICIOS = "/api/parte-beneficios";
    private static final String PARTE_BENEFICIOS_ACTIVAS = "/api/parte-beneficios/activas";
    private static final String APORTES_POR_NUTRIENTE =
            "/api/aportes-nutricionales/nutriente/" + ID_INEXISTENTE;
    private static final String EPOCAS = "/api/epocas-cosecha";

    /**
     * Los cuatro que no necesitan parametros. El quinto --{@code EPOCAS}--
     * lleva un {@code @RequestParam} obligatorio y va aparte.
     */
    private static final List<String> SIN_PARAMETROS = List.of(
            PREPARACIONES,
            PARTE_BENEFICIOS,
            PARTE_BENEFICIOS_ACTIVAS,
            APORTES_POR_NUTRIENTE);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private EspecieParteComestibleRepository especieParteComestibleRepository;

    @MockitoBean
    private EmailService emailService;

    private String tokenCon(String... permisos) {
        UsuarioResponse usuario = UsuarioResponse.builder()
                .id(999L)
                .nombres("Prueba")
                .apellidos("ListadosGlobales")
                .correo("prueba.listadosglobales@local")
                .nombreUsuario("prueba.listadosglobales")
                .activo(true)
                .build();

        return "Bearer " + jwtService.emitir(usuario, List.of("CONSULTOR"), List.of(permisos)).token();
    }

    /** Una parte comestible cualquiera: el listado de epocas no corre sin una. */
    private Optional<EspecieParteComestible> algunaParte() {
        return especieParteComestibleRepository.findAll().stream().findFirst();
    }

    // ---------- El anonimo ya no llega ----------

    @Test
    void listadosGlobales_sinToken_devuelven401() throws Exception {
        for (String ruta : SIN_PARAMETROS) {
            mockMvc.perform(get(ruta))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.estado").value(401));
        }
    }

    /**
     * El parametro obligatorio va puesto a proposito. Sin el la respuesta seria
     * 401 igual --el filtro corre antes de que Spring MVC resuelva los
     * argumentos-- pero la prueba dejaria de distinguir el corte de seguridad
     * de un 400 por el parametro que falta.
     */
    @Test
    void listadoDeEpocas_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get(EPOCAS).param("idEspecieParte", ID_INEXISTENTE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.estado").value(401));
    }

    // ---------- Con token, pero sin el permiso ----------

    @Test
    void listadosGlobales_conTokenSinEspecieConsultar_devuelven403() throws Exception {
        for (String ruta : SIN_PARAMETROS) {
            mockMvc.perform(get(ruta)
                            .header(HttpHeaders.AUTHORIZATION, tokenCon(OTRO_PERMISO)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.estado").value(403));
        }

        mockMvc.perform(get(EPOCAS)
                        .param("idEspecieParte", ID_INEXISTENTE)
                        .header(HttpHeaders.AUTHORIZATION, tokenCon(OTRO_PERMISO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.estado").value(403));
    }

    // ---------- Con ESPECIE_CONSULTAR ----------

    @Test
    void listadosGlobales_conEspecieConsultar_devuelven200() throws Exception {
        for (String ruta : SIN_PARAMETROS) {
            mockMvc.perform(get(ruta)
                            .header(HttpHeaders.AUTHORIZATION, tokenCon(ESPECIE_CONSULTAR)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void listadoDeEpocas_conEspecieConsultar_devuelve200() throws Exception {
        Optional<EspecieParteComestible> parte = algunaParte();
        Assumptions.assumeTrue(parte.isPresent(), "no hay partes comestibles en la base");

        mockMvc.perform(get(EPOCAS)
                        .param("idEspecieParte", parte.get().getIdEspecieParte().toString())
                        .header(HttpHeaders.AUTHORIZATION, tokenCon(ESPECIE_CONSULTAR)))
                .andExpect(status().isOk());
    }

    /**
     * La misma afirmacion sin depender de lo que haya sembrado: con el permiso,
     * una parte que no existe da 404 y no 403. El 404 sale del servicio, o sea
     * que el guard dejo pasar; es el razonamiento de
     * {@link AutorizacionPorPermisoTest}.
     */
    @Test
    void listadoDeEpocas_conEspecieConsultar_atraviesaElGuard() throws Exception {
        mockMvc.perform(get(EPOCAS)
                        .param("idEspecieParte", ID_INEXISTENTE)
                        .header(HttpHeaders.AUTHORIZATION, tokenCon(ESPECIE_CONSULTAR)))
                .andExpect(status().isNotFound());
    }

    // ---------- Lo que seguia abierto sigue abierto ----------

    /**
     * El recorte de {@code CATALOGO_PUBLICO} toca cinco rutas exactas, no las
     * familias enteras. Los hermanos de estos listados que si reciben el id del
     * padre --y que por eso ya filtran por el estado de la ficha dentro del
     * servicio-- tienen que seguir atendiendo al anonimo. Lo que se mide es que
     * no sean 401 ni 403: con el id 999999 lo que corresponde es la respuesta
     * de cada servicio, no un corte de seguridad.
     */
    @Test
    void losListadosPorPadre_siguenAbiertosSinToken() throws Exception {
        mockMvc.perform(get(PREPARACIONES + "/parte/" + ID_INEXISTENTE))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(PARTE_BENEFICIOS + "/" + ID_INEXISTENTE + "/" + ID_INEXISTENTE))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(EPOCAS + "/" + ID_INEXISTENTE))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/aportes-nutricionales/" + ID_INEXISTENTE))
                .andExpect(status().isNotFound());

        // La excepcion documentada de la familia: nunca valido que la parte
        // existiera, asi que contesta 200 con lista vacia.
        mockMvc.perform(get("/api/aportes-nutricionales/especie-parte/" + ID_INEXISTENTE))
                .andExpect(status().isOk());
    }

    /** Los catalogos que no cuelgan de ninguna especie no entraron en el cambio. */
    @Test
    void catalogosIndependientes_siguenAbiertosSinToken() throws Exception {
        mockMvc.perform(get("/api/nutrientes")).andExpect(status().isOk());
        mockMvc.perform(get("/api/beneficios")).andExpect(status().isOk());
        mockMvc.perform(get("/api/partes-planta")).andExpect(status().isOk());
    }
}
