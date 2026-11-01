package sv.edu.ues.fmp.flora.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.hamcrest.Matchers;
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
import sv.edu.ues.fmp.flora.entity.Especie;
import sv.edu.ues.fmp.flora.entity.enums.EstadoPublicacion;
import sv.edu.ues.fmp.flora.repository.EspecieRepository;
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
 * <h2>Por que usa los datos de la base y no los siembra</h2>
 * Una especie PUBLICADA no se puede insertar a mano sin pelear con
 * {@code ck_especie_publicacion}, que exige {@code validada_por},
 * {@code publicada_por} y {@code fecha_publicacion} coherentes. La migracion
 * {@code V9} ya siembra las ocho fichas con los estados que hacen falta (cinco
 * PUBLICADA, dos EN_REVISION, una BORRADOR), asi que las pruebas las buscan
 * por estado. Cuando la base no las tiene, la prueba se salta en vez de
 * fallar: es el mismo criterio que {@link AutenticacionJwtTest} usa para los
 * usuarios.
 */
@SpringBootTest
@AutoConfigureMockMvc
// Solo lecturas, pero el rollback deja claro que estas pruebas no tocan los
// datos sembrados que usan.
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
    private EspecieRepository especieRepository;

    @MockitoBean
    private EmailService emailService;

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

    /** Una ficha que NO esta publicada: BORRADOR o EN_REVISION, lo que haya. */
    private Optional<Especie> algunaNoPublicada() {
        return especieRepository.findAll().stream()
                .filter(especie -> especie.getEstadoPublicacion() != EstadoPublicacion.PUBLICADA)
                .findFirst();
    }

    private Optional<Especie> algunaPublicada() {
        return especieRepository
                .findByEstadoPublicacionAndActivaTrue(EstadoPublicacion.PUBLICADA)
                .stream()
                .findFirst();
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
        Optional<Especie> borrador = algunaNoPublicada();
        Assumptions.assumeTrue(borrador.isPresent(), "no hay fichas sin publicar en la base");

        mockMvc.perform(get(LISTADO_INTERNO + "/" + borrador.get().getIdEspecie()))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Por el detalle publico la ficha sin publicar no es 401 ni 403 sino 404,
     * con el mismo mensaje que un id que no existe. Si respondiera distinto,
     * probando numeros se podria averiguar que fichas hay en preparacion.
     */
    @Test
    void detallePublicoDeUnBorrador_devuelve404ComoSiNoExistiera() throws Exception {
        Optional<Especie> borrador = algunaNoPublicada();
        Assumptions.assumeTrue(borrador.isPresent(), "no hay fichas sin publicar en la base");

        Long idBorrador = borrador.get().getIdEspecie();

        mockMvc.perform(get(LISTADO_PUBLICO + "/" + idBorrador))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No existe una especie con id " + idBorrador));

        mockMvc.perform(get(LISTADO_PUBLICO + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No existe una especie con id 999999"));
    }

    @Test
    void listadoPublico_noIncluyeLasFichasSinPublicar() throws Exception {
        Optional<Especie> borrador = algunaNoPublicada();
        Assumptions.assumeTrue(borrador.isPresent(), "no hay fichas sin publicar en la base");

        mockMvc.perform(get(LISTADO_PUBLICO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idEspecie")
                        .value(Matchers.not(Matchers.hasItem(borrador.get().getIdEspecie().intValue()))))
                .andExpect(jsonPath("$[*].estadoPublicacion")
                        .value(Matchers.everyItem(Matchers.is("PUBLICADA"))));
    }

    // ---------- ESPECIE_CONSULTAR por fin sirve para algo ----------

    @Test
    void listadoInterno_conEspecieConsultar_devuelve200() throws Exception {
        mockMvc.perform(get(LISTADO_INTERNO)
                        .header(HttpHeaders.AUTHORIZATION, tokenCon(ESPECIE_CONSULTAR)))
                .andExpect(status().isOk());
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
        Optional<Especie> borrador = algunaNoPublicada();
        Assumptions.assumeTrue(borrador.isPresent(), "no hay fichas sin publicar en la base");

        Especie ficha = borrador.get();

        mockMvc.perform(get(LISTADO_INTERNO)
                        .param("estado", ficha.getEstadoPublicacion().name())
                        .header(HttpHeaders.AUTHORIZATION, tokenCon(ESPECIE_CONSULTAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idEspecie")
                        .value(Matchers.hasItem(ficha.getIdEspecie().intValue())));
    }

    @Test
    void detalleInternoDeUnBorrador_conEspecieConsultar_devuelve200() throws Exception {
        Optional<Especie> borrador = algunaNoPublicada();
        Assumptions.assumeTrue(borrador.isPresent(), "no hay fichas sin publicar en la base");

        Especie ficha = borrador.get();

        mockMvc.perform(get(LISTADO_INTERNO + "/" + ficha.getIdEspecie())
                        .header(HttpHeaders.AUTHORIZATION, tokenCon(ESPECIE_CONSULTAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idEspecie").value(ficha.getIdEspecie()))
                .andExpect(jsonPath("$.estadoPublicacion")
                        .value(ficha.getEstadoPublicacion().name()));
    }

    // ---------- El catalogo publico no se rompio ----------

    @Test
    void listadoPublico_sigueAbiertoSinToken() throws Exception {
        mockMvc.perform(get(LISTADO_PUBLICO))
                .andExpect(status().isOk());
    }

    @Test
    void detallePublicoDeUnaPublicada_sigueAbiertoSinToken() throws Exception {
        Optional<Especie> publicada = algunaPublicada();
        Assumptions.assumeTrue(publicada.isPresent(), "no hay fichas publicadas en la base");

        Especie ficha = publicada.get();

        mockMvc.perform(get(LISTADO_PUBLICO + "/" + ficha.getIdEspecie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idEspecie").value(ficha.getIdEspecie()))
                .andExpect(jsonPath("$.estadoPublicacion").value("PUBLICADA"));
    }

    /**
     * Los sub-recursos de una especie siguen abiertos en GET porque son los que
     * pinta la ficha publica. Lo que esta prueba fija es que el cambio no los
     * cerro; que tampoco filtren por el estado del padre esta anotado como
     * pendiente en {@link SecurityConfig}.
     */
    @Test
    void subRecursosDeUnaEspecie_siguenAbiertosSinToken() throws Exception {
        Optional<Especie> publicada = algunaPublicada();
        Assumptions.assumeTrue(publicada.isPresent(), "no hay fichas publicadas en la base");

        Long id = publicada.get().getIdEspecie();

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
