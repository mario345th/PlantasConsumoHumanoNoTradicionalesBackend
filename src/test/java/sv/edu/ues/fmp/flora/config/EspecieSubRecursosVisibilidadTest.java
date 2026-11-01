package sv.edu.ues.fmp.flora.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import sv.edu.ues.fmp.flora.entity.Especie;
import sv.edu.ues.fmp.flora.entity.enums.EstadoPublicacion;
import sv.edu.ues.fmp.flora.repository.EspecieRepository;
import sv.edu.ues.fmp.flora.service.EmailService;
import sv.edu.ues.fmp.flora.service.JwtService;

/**
 * Pruebas de la ultima via por la que se veia el contenido de un borrador: los
 * seis sub-recursos de una especie.
 *
 * <h2>Lo que estaba mal</h2>
 * Las consultas de la ficha ya exigian ESPECIE_CONSULTAR, pero
 * {@code GET /api/especies/{id}/imagenes} y sus cinco hermanos seguian
 * publicos y ninguno miraba el estado del padre. Con el id de un borrador
 * --que se adivina probando numeros-- se veian sus imagenes, sus videos y sus
 * nombres comunes sin token.
 *
 * <h2>Lo que se comprueba, y con los seis</h2>
 * Las cuatro combinaciones de la matriz, cada una contra los seis
 * sub-recursos en un bucle: recorrerlos todos cuesta lo mismo que elegir dos y
 * deja fijado que ninguno se quedo sin el guard.
 * <p>
 * La pieza que mas importa es que el 404 del borrador y el de un id que no
 * existe sean <strong>la misma respuesta</strong>, no solo el mismo codigo. Si
 * el mensaje se separara, comparar las dos volveria a delatar que fichas hay
 * en preparacion, que es justo lo que el cambio quiere evitar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EspecieSubRecursosVisibilidadTest {

    private static final String ESPECIE_CONSULTAR = "ESPECIE_CONSULTAR";

    /** Un id que no existe en ninguna base, para el 404 de referencia. */
    private static final long ID_INEXISTENTE = 999999L;

    /** Los seis sub-recursos, tal como cuelgan de /api/especies/{id}. */
    private static final List<String> SUB_RECURSOS = List.of(
            "fuentes",
            "habitats",
            "imagenes",
            "nombres-comunes",
            "partes-comestibles",
            "videos");

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
                .apellidos("SubRecursos")
                .correo("prueba.subrecursos@local")
                .nombreUsuario("prueba.subrecursos")
                .activo(true)
                .build();

        return "Bearer " + jwtService.emitir(usuario, List.of("CONSULTOR"), List.of(permisos)).token();
    }

    private static String ruta(Object idEspecie, String subRecurso) {
        return "/api/especies/" + idEspecie + "/" + subRecurso;
    }

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

    private Long idDeUnBorrador() {
        Optional<Especie> borrador = algunaNoPublicada();
        Assumptions.assumeTrue(borrador.isPresent(), "no hay fichas sin publicar en la base");
        return borrador.get().getIdEspecie();
    }

    private Long idDeUnaPublicada() {
        Optional<Especie> publicada = algunaPublicada();
        Assumptions.assumeTrue(publicada.isPresent(), "no hay fichas publicadas en la base");
        return publicada.get().getIdEspecie();
    }

    // ---------- 1. El anonimo no ve el contenido de un borrador ----------

    @Test
    void anonimo_subRecursosDeUnBorrador_devuelve404() throws Exception {
        Long idBorrador = idDeUnBorrador();

        for (String subRecurso : SUB_RECURSOS) {
            mockMvc.perform(get(ruta(idBorrador, subRecurso)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.estado").value(404))
                    .andExpect(jsonPath("$.error").value("Recurso no encontrado"))
                    .andExpect(jsonPath("$.mensaje")
                            .value("No existe una especie con id " + idBorrador));
        }
    }

    /**
     * La prueba del oraculo: el cuerpo que recibe el anonimo por un borrador y
     * el que recibe por un id inventado tienen que ser el mismo, salvo el id
     * que el llamo escribio y la ruta y el timestamp que cambian solos. Si
     * difirieran en algo mas, la diferencia serviria para enumerar.
     */
    @Test
    void anonimo_elBorradorYUnIdInexistenteRespondenIgual() throws Exception {
        Long idBorrador = idDeUnBorrador();

        for (String subRecurso : SUB_RECURSOS) {
            String deBorrador = mockMvc.perform(get(ruta(idBorrador, subRecurso)))
                    .andExpect(status().isNotFound())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            String deIdInventado = mockMvc.perform(get(ruta(ID_INEXISTENTE, subRecurso)))
                    .andExpect(status().isNotFound())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            // Se normaliza lo que por fuerza tiene que diferir: el id dentro
            // del mensaje, el id dentro de la ruta y el instante.
            assertEquals(
                    normalizar(deIdInventado, ID_INEXISTENTE),
                    normalizar(deBorrador, idBorrador),
                    "el 404 del borrador y el del id inexistente difieren en " + subRecurso);
        }
    }

    private static String normalizar(String cuerpo, Long id) {
        return cuerpo
                .replace(String.valueOf(id), "{id}")
                .replaceAll("\"timestamp\":\"[^\"]*\"", "\"timestamp\":\"{instante}\"");
    }

    /**
     * Un token sin ESPECIE_CONSULTAR recibe 404, no 403. El 403 diria "existe
     * pero no te alcanza", que es exactamente la confirmacion que no se quiere
     * dar; al dar 404 el borrador sigue siendo indistinguible de un id que no
     * existe tambien para quien ya tiene cuenta.
     */
    @Test
    void conTokenSinEspecieConsultar_subRecursosDeUnBorrador_devuelve404YNo403() throws Exception {
        Long idBorrador = idDeUnBorrador();

        for (String subRecurso : SUB_RECURSOS) {
            mockMvc.perform(get(ruta(idBorrador, subRecurso))
                            .header(HttpHeaders.AUTHORIZATION, tokenCon("MULTIMEDIA_GESTIONAR")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.mensaje")
                            .value("No existe una especie con id " + idBorrador));
        }
    }

    // ---------- 2. La ficha publica sigue completa ----------

    @Test
    void anonimo_subRecursosDeUnaPublicada_devuelve200() throws Exception {
        Long idPublicada = idDeUnaPublicada();

        for (String subRecurso : SUB_RECURSOS) {
            mockMvc.perform(get(ruta(idPublicada, subRecurso)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    /** Los parametros de filtro de los que lo tienen siguen funcionando igual. */
    @Test
    void anonimo_subRecursosDeUnaPublicada_conSoloActivos_devuelve200() throws Exception {
        Long idPublicada = idDeUnaPublicada();

        mockMvc.perform(get(ruta(idPublicada, "imagenes")).param("soloActivas", "true"))
                .andExpect(status().isOk());
        mockMvc.perform(get(ruta(idPublicada, "videos")).param("soloActivos", "true"))
                .andExpect(status().isOk());
        mockMvc.perform(get(ruta(idPublicada, "nombres-comunes")).param("soloActivos", "true"))
                .andExpect(status().isOk());
        mockMvc.perform(get(ruta(idPublicada, "partes-comestibles")).param("soloActivas", "true"))
                .andExpect(status().isOk());
    }

    // ---------- 3. Con el permiso si se ven los borradores ----------

    @Test
    void conEspecieConsultar_subRecursosDeUnBorrador_devuelve200() throws Exception {
        Long idBorrador = idDeUnBorrador();

        for (String subRecurso : SUB_RECURSOS) {
            mockMvc.perform(get(ruta(idBorrador, subRecurso))
                            .header(HttpHeaders.AUTHORIZATION, tokenCon(ESPECIE_CONSULTAR)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    // ---------- 4. El id inexistente sigue dando 404 ----------

    @Test
    void idDeEspecieInexistente_devuelve404ConOSinToken() throws Exception {
        for (String subRecurso : SUB_RECURSOS) {
            mockMvc.perform(get(ruta(ID_INEXISTENTE, subRecurso)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.mensaje")
                            .value("No existe una especie con id " + ID_INEXISTENTE));

            // Tener el permiso no hace aparecer lo que no existe.
            mockMvc.perform(get(ruta(ID_INEXISTENTE, subRecurso))
                            .header(HttpHeaders.AUTHORIZATION, tokenCon(ESPECIE_CONSULTAR)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.mensaje")
                            .value("No existe una especie con id " + ID_INEXISTENTE));
        }
    }

    // ---------- Que el detalle publico siga alineado ----------

    /**
     * El detalle publico de la ficha y los sub-recursos tienen que contestar lo
     * mismo ante un borrador: los dos comparten el texto del 404 por
     * {@code VisibilidadDeEspecie.noEncontrada}, y esta prueba es la que avisa
     * si alguien separa uno de los dos.
     */
    @Test
    void elDetallePublicoYLosSubRecursosDanElMismo404ParaUnBorrador() throws Exception {
        Long idBorrador = idDeUnBorrador();

        String esperado = "No existe una especie con id " + idBorrador;

        mockMvc.perform(get("/api/especies/publicadas/" + idBorrador))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(esperado));

        mockMvc.perform(get(ruta(idBorrador, "imagenes")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(esperado));
    }
}
