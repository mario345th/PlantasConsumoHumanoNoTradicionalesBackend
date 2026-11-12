package sv.edu.ues.fmp.flora.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
 * Pruebas de la via paralela: las rutas <strong>planas</strong> que llevan el
 * id del hijo en vez del de la especie.
 *
 * <h2>Lo que estaba mal</h2>
 * Cerrados los seis GET anidados {@code /api/especies/{id}/...}, quedaba la
 * puerta de al lado: {@code GET /api/imagenes-especie/{id}} y sus hermanos
 * aceptan el id de la imagen, no el de la ficha, y no miraban el estado del
 * padre. Los ids son enteros pequeños y consecutivos, asi que probar numeros
 * seguia sacando la URL de la imagen de un borrador, su video, sus nombres
 * comunes, sus preparaciones y hasta sus coordenadas.
 *
 * <h2>Como se eligen los datos, y por que cambio</h2>
 * Antes cada recurso se buscaba en la base: se recorrian las once tablas
 * navegando la cadena hasta la especie, se quedaba el primer hijo cuyo padre
 * estuviera en el estado buscado, los recursos sin fila se omitian y, si no
 * quedaba ninguno, la prueba se saltaba. Con {@code V14} --que dejo las quince
 * fichas en PUBLICADA-- el conjunto de "hijos de fichas sin publicar" quedo
 * vacio y <strong>seis pruebas de esta clase dejaron de ejecutarse</strong>,
 * dentro de un BUILD SUCCESS.
 * <p>
 * Ahora {@link SiembraDeFichas} siembra tres fichas --BORRADOR, EN_REVISION y
 * PUBLICADA-- cada una con el arbol completo, y los catorce casos se arman con
 * los ids de lo sembrado. La prueba ya no depende de lo que haya en la base:
 * cubre siempre los catorce recursos en los tres estados, incluida la cadena de
 * tres saltos {@code imagen -> preparacion -> parte comestible -> especie}, que
 * era la que menos datos tenia y la unica con una forma distinta a las demas.
 * <p>
 * Todo lo sembrado vive en la transaccion de la prueba y el rollback de
 * {@code @Transactional} se lo lleva.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EspecieRutasPlanasVisibilidadTest {

    private static final String ESPECIE_CONSULTAR = "ESPECIE_CONSULTAR";

    private static final long ID_INEXISTENTE = 999999L;

    /** Un recurso a probar: como se llama y la ruta plana que lo devuelve. */
    private record Caso(String recurso, String ruta) {
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private EntityManager entityManager;

    @MockitoBean private EmailService emailService;

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

    private List<Ficha> sinPublicar() {
        return List.of(borrador, enRevision);
    }

    private String tokenCon(String... permisos) {
        UsuarioResponse usuario = UsuarioResponse.builder()
                .id(999L)
                .nombres("Prueba")
                .apellidos("RutasPlanas")
                .correo("prueba.rutasplanas@local")
                .nombreUsuario("prueba.rutasplanas")
                .activo(true)
                .build();

        return "Bearer " + jwtService.emitir(usuario, List.of("CONSULTOR"), List.of(permisos)).token();
    }

    /**
     * Los catorce recursos del alcance, armados con los ids de la ficha
     * sembrada. Los diez primeros llevan el id del hijo; los cuatro ultimos
     * cuelgan de un id intermedio (la parte, la preparacion, la especie) y
     * tambien tenian que entrar por el padre.
     */
    private List<Caso> casos(Ficha ficha) {
        return List.of(
                new Caso("imagen de especie",
                        "/api/imagenes-especie/" + ficha.imagenEspecie().getIdImagen()),
                new Caso("video de especie",
                        "/api/videos-especie/" + ficha.videoEspecie().getIdVideo()),
                new Caso("nombre comun",
                        "/api/nombres-comunes/" + ficha.nombreComun().getIdNombreComun()),
                new Caso("parte comestible",
                        "/api/partes-comestibles/" + ficha.parte().getIdEspecieParte()),
                new Caso("epoca de cosecha",
                        "/api/epocas-cosecha/" + ficha.epocaCosecha().getIdEpocaCosecha()),
                new Caso("aporte nutricional",
                        "/api/aportes-nutricionales/" + ficha.aporte().getIdAporte()),
                new Caso("preparacion de consumo",
                        "/api/preparaciones-consumo/" + ficha.preparacion().getIdPreparacion()),
                new Caso("imagen de preparacion",
                        "/api/imagenes-preparacion/"
                                + ficha.imagenPreparacion().getIdImagenPreparacion()),
                new Caso("video de preparacion",
                        "/api/videos-preparacion/"
                                + ficha.videoPreparacion().getIdVideoPreparacion()),
                new Caso("parte-beneficio",
                        "/api/parte-beneficios/"
                                + ficha.parteBeneficio().getId().getIdEspecieParte() + "/"
                                + ficha.parteBeneficio().getId().getIdBeneficio()),
                new Caso("preparaciones de una parte",
                        "/api/preparaciones-consumo/parte/" + ficha.parte().getIdEspecieParte()),
                new Caso("imagenes de una preparacion",
                        "/api/imagenes-preparacion/preparacion/"
                                + ficha.preparacion().getIdPreparacion()),
                new Caso("videos de una preparacion",
                        "/api/videos-preparacion/preparacion/"
                                + ficha.preparacion().getIdPreparacion()),
                new Caso("ubicaciones de una especie",
                        "/api/ubicaciones/especie/" + ficha.id()));
    }

    // ---------- 1. El anonimo no ve los hijos de un borrador ----------

    @Test
    void anonimo_hijoDeUnBorrador_devuelve404() throws Exception {
        for (Ficha ficha : sinPublicar()) {
            for (Caso caso : casos(ficha)) {
                mockMvc.perform(get(caso.ruta()))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.estado").value(404))
                        .andExpect(jsonPath("$.error").value("Recurso no encontrado"));
            }
        }
    }

    /**
     * La prueba del oraculo, la que de verdad cierra la puerta: el cuerpo que
     * recibe el anonimo por el hijo de un borrador tiene que ser identico al
     * que recibe por un id inventado del mismo recurso. Si el mensaje
     * difiriera --"no existe una especie con id 8" frente a "no existe la
     * imagen con id 999"-- la diferencia diria que esa imagen existe y que su
     * ficha no esta publicada.
     */
    @Test
    void anonimo_elHijoDeUnBorradorYUnIdInventadoRespondenIgual() throws Exception {
        for (Ficha ficha : sinPublicar()) {
            List<Caso> casos = casos(ficha);

            for (Caso caso : casos) {
                // La ruta del id inventado es la misma cambiando el ultimo
                // segmento numerico por uno que no existe.
                String rutaInventada = caso.ruta()
                        .replaceAll("[0-9]+$", String.valueOf(ID_INEXISTENTE));

                String delBorrador = cuerpoDe(caso.ruta());
                String delInventado = cuerpoDe(rutaInventada);

                assertEquals(
                        normalizar(delInventado),
                        normalizar(delBorrador),
                        "el 404 del hijo de un borrador y el de un id inventado difieren en "
                                + caso.recurso() + " (" + caso.ruta() + ")");
            }
        }
    }

    private String cuerpoDe(String ruta) throws Exception {
        return mockMvc.perform(get(ruta))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /** Borra lo que por fuerza difiere: los ids, la ruta y el instante. */
    private static String normalizar(String cuerpo) {
        return cuerpo
                .replaceAll("[0-9]{1,}", "{n}")
                .replaceAll("\"timestamp\":\"[^\"]*\"", "\"timestamp\":\"{instante}\"")
                .replaceAll("\"ruta\":\"[^\"]*\"", "\"ruta\":\"{ruta}\"");
    }

    /**
     * Un token sin ESPECIE_CONSULTAR recibe 404, no 403: el 403 confirmaria que
     * ese id esta ocupado.
     */
    @Test
    void conTokenSinEspecieConsultar_hijoDeUnBorrador_devuelve404YNo403() throws Exception {
        for (Ficha ficha : sinPublicar()) {
            for (Caso caso : casos(ficha)) {
                mockMvc.perform(get(caso.ruta())
                                .header(HttpHeaders.AUTHORIZATION, tokenCon("MULTIMEDIA_GESTIONAR")))
                        .andExpect(status().isNotFound());
            }
        }
    }

    // ---------- 2. La ficha publica sigue completa ----------

    @Test
    void anonimo_hijoDeUnaPublicada_devuelve200() throws Exception {
        for (Caso caso : casos(publicada)) {
            mockMvc.perform(get(caso.ruta()))
                    .andExpect(status().isOk());
        }
    }

    // ---------- 3. Con el permiso si se ven ----------

    @Test
    void conEspecieConsultar_hijoDeUnBorrador_devuelve200() throws Exception {
        for (Ficha ficha : sinPublicar()) {
            for (Caso caso : casos(ficha)) {
                mockMvc.perform(get(caso.ruta())
                                .header(HttpHeaders.AUTHORIZATION, tokenCon(ESPECIE_CONSULTAR)))
                        .andExpect(status().isOk());
            }
        }
    }

    // ---------- 4. El hijo inexistente sigue dando 404 ----------

    @Test
    void idInexistente_devuelve404ConOSinToken() throws Exception {
        List<String> rutas = List.of(
                "/api/imagenes-especie/" + ID_INEXISTENTE,
                "/api/videos-especie/" + ID_INEXISTENTE,
                "/api/nombres-comunes/" + ID_INEXISTENTE,
                "/api/partes-comestibles/" + ID_INEXISTENTE,
                "/api/epocas-cosecha/" + ID_INEXISTENTE,
                "/api/aportes-nutricionales/" + ID_INEXISTENTE,
                "/api/preparaciones-consumo/" + ID_INEXISTENTE,
                "/api/imagenes-preparacion/" + ID_INEXISTENTE,
                "/api/videos-preparacion/" + ID_INEXISTENTE,
                "/api/parte-beneficios/" + ID_INEXISTENTE + "/" + ID_INEXISTENTE,
                "/api/preparaciones-consumo/parte/" + ID_INEXISTENTE,
                "/api/imagenes-preparacion/preparacion/" + ID_INEXISTENTE,
                "/api/videos-preparacion/preparacion/" + ID_INEXISTENTE,
                "/api/ubicaciones/especie/" + ID_INEXISTENTE);

        for (String ruta : rutas) {
            mockMvc.perform(get(ruta))
                    .andExpect(status().isNotFound());

            mockMvc.perform(get(ruta)
                            .header(HttpHeaders.AUTHORIZATION, tokenCon(ESPECIE_CONSULTAR)))
                    .andExpect(status().isNotFound());
        }
    }

    /**
     * La excepcion de la familia: {@code /api/aportes-nutricionales/especie-parte/{id}}
     * nunca valido que la parte existiera y devolvia 200 con lista vacia para
     * un id inventado. Se dejo asi, y la parte de un borrador devuelve tambien
     * la lista vacia: las dos respuestas siguen siendo la misma, que es lo que
     * se busca, sin cambiarle el contrato al frontend.
     * <p>
     * La parte sembrada tiene un aporte, asi que la lista vacia aqui solo puede
     * venir del guard: si se cayera, en su lugar apareceria ese aporte.
     */
    @Test
    void aportesPorEspecieParte_elBorradorYUnIdInventadoDevuelvenListaVacia() throws Exception {
        for (Ficha ficha : sinPublicar()) {
            mockMvc.perform(get("/api/aportes-nutricionales/especie-parte/"
                            + ficha.parte().getIdEspecieParte()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }

        mockMvc.perform(get("/api/aportes-nutricionales/especie-parte/" + ID_INEXISTENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    /** Con el permiso, la misma ruta si devuelve lo que haya. */
    @Test
    void aportesPorEspecieParte_conEspecieConsultar_noSeVacia() throws Exception {
        for (Ficha ficha : sinPublicar()) {
            mockMvc.perform(get("/api/aportes-nutricionales/especie-parte/"
                            + ficha.parte().getIdEspecieParte())
                            .header(HttpHeaders.AUTHORIZATION, tokenCon(ESPECIE_CONSULTAR)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isNotEmpty());
        }

        // Y sin permiso, la parte de una ficha publicada tampoco se vacia: el
        // guard filtra por el estado del padre, no por tener token.
        mockMvc.perform(get("/api/aportes-nutricionales/especie-parte/"
                        + publicada.parte().getIdEspecieParte()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isNotEmpty());
    }

    // ---------- La cadena de tres saltos ----------

    /**
     * La cadena mas larga del modelo --{@code imagen -> preparacion -> parte
     * comestible -> especie}-- en sus cinco rutas, con los tres estados. Es la
     * unica con una forma distinta a las demas y la que menos datos tenia: la
     * base de desarrollo no traia preparaciones de consumo, asi que esta prueba
     * se las sembraba ella misma. Ahora la siembra es la de la clase y lo que
     * queda aqui es la comprobacion, que ya no puede saltarse.
     */
    @Test
    void cadenaDeTresSaltos_imagenYVideoDePreparacion_respetanElEstadoDeLaFicha() throws Exception {
        for (Ficha ficha : sinPublicar()) {
            for (String ruta : cadenaDe(ficha)) {
                mockMvc.perform(get(ruta))
                        .andExpect(status().isNotFound());

                mockMvc.perform(get(ruta)
                                .header(HttpHeaders.AUTHORIZATION, tokenCon(ESPECIE_CONSULTAR)))
                        .andExpect(status().isOk());
            }
        }

        for (String ruta : cadenaDe(publicada)) {
            mockMvc.perform(get(ruta))
                    .andExpect(status().isOk());
        }
    }

    private List<String> cadenaDe(Ficha ficha) {
        return List.of(
                "/api/preparaciones-consumo/" + ficha.preparacion().getIdPreparacion(),
                "/api/imagenes-preparacion/" + ficha.imagenPreparacion().getIdImagenPreparacion(),
                "/api/videos-preparacion/" + ficha.videoPreparacion().getIdVideoPreparacion(),
                "/api/imagenes-preparacion/preparacion/" + ficha.preparacion().getIdPreparacion(),
                "/api/videos-preparacion/preparacion/" + ficha.preparacion().getIdPreparacion());
    }

    // ---------- Los catalogos independientes no se tocaron ----------

    /**
     * Los catalogos que no pertenecen a ninguna especie siguen abiertos: son
     * publicos legitimos y cerrarlos habria sido pasarse del alcance.
     */
    @Test
    void losCatalogosIndependientes_siguenAbiertosSinToken() throws Exception {
        mockMvc.perform(get("/api/habitats")).andExpect(status().isOk());
        mockMvc.perform(get("/api/nutrientes")).andExpect(status().isOk());
        mockMvc.perform(get("/api/beneficios")).andExpect(status().isOk());
        mockMvc.perform(get("/api/partes-planta")).andExpect(status().isOk());
        mockMvc.perform(get("/api/departamentos")).andExpect(status().isOk());
        mockMvc.perform(get("/api/municipios")).andExpect(status().isOk());
        mockMvc.perform(get("/api/fuentes")).andExpect(status().isOk());
    }
}
