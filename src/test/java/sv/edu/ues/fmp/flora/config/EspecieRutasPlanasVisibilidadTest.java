package sv.edu.ues.fmp.flora.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

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
import sv.edu.ues.fmp.flora.entity.EspecieParteComestible;
import sv.edu.ues.fmp.flora.entity.ImagenPreparacion;
import sv.edu.ues.fmp.flora.entity.PreparacionConsumo;
import sv.edu.ues.fmp.flora.entity.VideoPreparacion;
import sv.edu.ues.fmp.flora.entity.enums.EstadoPublicacion;
import sv.edu.ues.fmp.flora.entity.enums.TipoConsumo;
import sv.edu.ues.fmp.flora.repository.AporteNutricionalRepository;
import sv.edu.ues.fmp.flora.repository.EpocaCosechaRepository;
import sv.edu.ues.fmp.flora.repository.EspecieParteComestibleRepository;
import sv.edu.ues.fmp.flora.repository.EspecieRepository;
import sv.edu.ues.fmp.flora.repository.ImagenEspecieRepository;
import sv.edu.ues.fmp.flora.repository.ImagenPreparacionRepository;
import sv.edu.ues.fmp.flora.repository.NombreComunRepository;
import sv.edu.ues.fmp.flora.repository.ParteBeneficioRepository;
import sv.edu.ues.fmp.flora.repository.PreparacionConsumoRepository;
import sv.edu.ues.fmp.flora.repository.VideoEspecieRepository;
import sv.edu.ues.fmp.flora.repository.VideoPreparacionRepository;
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
 * <h2>Como se eligen los datos</h2>
 * Para cada recurso se busca en la base una fila cuya especie padre este sin
 * publicar y otra cuya especie este PUBLICADA, navegando la cadena completa
 * (hasta {@code imagen -> preparacion -> parte comestible -> especie}). El
 * recurso que no tenga fila para uno de los dos estados se omite de ese
 * escenario, y si no queda ninguno la prueba se salta: es el mismo criterio de
 * {@link EspecieSubRecursosVisibilidadTest}, pero aqui hace mas falta porque
 * los datos sembrados no cubren por igual los once recursos.
 * <p>
 * Cada prueba afirma al final que recorrio al menos un recurso, para que una
 * base vacia no se confunda con una prueba que paso.
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

    @Autowired private EspecieRepository especieRepository;
    @Autowired private ImagenEspecieRepository imagenEspecieRepository;
    @Autowired private VideoEspecieRepository videoEspecieRepository;
    @Autowired private NombreComunRepository nombreComunRepository;
    @Autowired private EspecieParteComestibleRepository especieParteComestibleRepository;
    @Autowired private EpocaCosechaRepository epocaCosechaRepository;
    @Autowired private AporteNutricionalRepository aporteNutricionalRepository;
    @Autowired private PreparacionConsumoRepository preparacionConsumoRepository;
    @Autowired private ImagenPreparacionRepository imagenPreparacionRepository;
    @Autowired private VideoPreparacionRepository videoPreparacionRepository;
    @Autowired private ParteBeneficioRepository parteBeneficioRepository;

    @MockitoBean private EmailService emailService;

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

    private static final Predicate<Especie> PUBLICADA =
            especie -> especie.getEstadoPublicacion() == EstadoPublicacion.PUBLICADA
                    && Boolean.TRUE.equals(especie.getActiva());

    /**
     * Primera fila cuyo padre cumpla el estado pedido, convertida en ruta.
     * Devuelve vacio cuando la base no tiene ninguna.
     */
    private <T> Optional<Caso> buscar(
            String recurso,
            List<T> filas,
            Function<T, Especie> aEspecie,
            Function<T, String> aRuta,
            boolean publicada) {

        return filas.stream()
                .filter(fila -> PUBLICADA.test(aEspecie.apply(fila)) == publicada)
                .findFirst()
                .map(fila -> new Caso(recurso, aRuta.apply(fila)));
    }

    /** Los once recursos del alcance, resueltos contra los datos de la base. */
    private List<Caso> casos(boolean publicada) {
        List<Caso> casos = new ArrayList<>();

        buscar("imagen de especie", imagenEspecieRepository.findAll(),
                i -> i.getEspecie(),
                i -> "/api/imagenes-especie/" + i.getIdImagen(), publicada)
                .ifPresent(casos::add);

        buscar("video de especie", videoEspecieRepository.findAll(),
                v -> v.getEspecie(),
                v -> "/api/videos-especie/" + v.getIdVideo(), publicada)
                .ifPresent(casos::add);

        buscar("nombre comun", nombreComunRepository.findAll(),
                n -> n.getEspecie(),
                n -> "/api/nombres-comunes/" + n.getIdNombreComun(), publicada)
                .ifPresent(casos::add);

        buscar("parte comestible", especieParteComestibleRepository.findAll(),
                p -> p.getEspecie(),
                p -> "/api/partes-comestibles/" + p.getIdEspecieParte(), publicada)
                .ifPresent(casos::add);

        buscar("epoca de cosecha", epocaCosechaRepository.findAll(),
                e -> e.getEspecieParteComestible().getEspecie(),
                e -> "/api/epocas-cosecha/" + e.getIdEpocaCosecha(), publicada)
                .ifPresent(casos::add);

        buscar("aporte nutricional", aporteNutricionalRepository.findAll(),
                a -> a.getEspecieParteComestible().getEspecie(),
                a -> "/api/aportes-nutricionales/" + a.getIdAporte(), publicada)
                .ifPresent(casos::add);

        buscar("preparacion de consumo", preparacionConsumoRepository.findAll(),
                p -> p.getEspecieParteComestible().getEspecie(),
                p -> "/api/preparaciones-consumo/" + p.getIdPreparacion(), publicada)
                .ifPresent(casos::add);

        buscar("imagen de preparacion", imagenPreparacionRepository.findAll(),
                i -> i.getPreparacion().getEspecieParteComestible().getEspecie(),
                i -> "/api/imagenes-preparacion/" + i.getIdImagenPreparacion(), publicada)
                .ifPresent(casos::add);

        buscar("video de preparacion", videoPreparacionRepository.findAll(),
                v -> v.getPreparacion().getEspecieParteComestible().getEspecie(),
                v -> "/api/videos-preparacion/" + v.getIdVideoPreparacion(), publicada)
                .ifPresent(casos::add);

        buscar("parte-beneficio", parteBeneficioRepository.findAll(),
                pb -> pb.getEspecieParteComestible().getEspecie(),
                pb -> "/api/parte-beneficios/" + pb.getId().getIdEspecieParte()
                        + "/" + pb.getId().getIdBeneficio(), publicada)
                .ifPresent(casos::add);

        // Listados que cuelgan de un id intermedio, no del hijo.
        buscar("preparaciones de una parte", especieParteComestibleRepository.findAll(),
                p -> p.getEspecie(),
                p -> "/api/preparaciones-consumo/parte/" + p.getIdEspecieParte(), publicada)
                .ifPresent(casos::add);

        buscar("imagenes de una preparacion", preparacionConsumoRepository.findAll(),
                p -> p.getEspecieParteComestible().getEspecie(),
                p -> "/api/imagenes-preparacion/preparacion/" + p.getIdPreparacion(), publicada)
                .ifPresent(casos::add);

        buscar("videos de una preparacion", preparacionConsumoRepository.findAll(),
                p -> p.getEspecieParteComestible().getEspecie(),
                p -> "/api/videos-preparacion/preparacion/" + p.getIdPreparacion(), publicada)
                .ifPresent(casos::add);

        buscar("ubicaciones de una especie", especieRepository.findAll(),
                especie -> especie,
                especie -> "/api/ubicaciones/especie/" + especie.getIdEspecie(), publicada)
                .ifPresent(casos::add);

        return casos;
    }

    // ---------- 1. El anonimo no ve los hijos de un borrador ----------

    @Test
    void anonimo_hijoDeUnBorrador_devuelve404() throws Exception {
        List<Caso> casos = casos(false);
        Assumptions.assumeFalse(casos.isEmpty(), "la base no tiene hijos de fichas sin publicar");

        for (Caso caso : casos) {
            mockMvc.perform(get(caso.ruta()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.estado").value(404))
                    .andExpect(jsonPath("$.error").value("Recurso no encontrado"));
        }

        assertFalse(casos.isEmpty());
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
        List<Caso> casos = casos(false);
        Assumptions.assumeFalse(casos.isEmpty(), "la base no tiene hijos de fichas sin publicar");

        int comparados = 0;

        for (Caso caso : casos) {
            // La ruta del id inventado es la misma cambiando el ultimo segmento
            // numerico por uno que no existe.
            String rutaInventada = caso.ruta().replaceAll("[0-9]+$", String.valueOf(ID_INEXISTENTE));

            String delBorrador = cuerpoDe(caso.ruta());
            String delInventado = cuerpoDe(rutaInventada);

            assertEquals(
                    normalizar(delInventado),
                    normalizar(delBorrador),
                    "el 404 del hijo de un borrador y el de un id inventado difieren en "
                            + caso.recurso() + " (" + caso.ruta() + ")");
            comparados++;
        }

        assertEquals(casos.size(), comparados);
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
        List<Caso> casos = casos(false);
        Assumptions.assumeFalse(casos.isEmpty(), "la base no tiene hijos de fichas sin publicar");

        for (Caso caso : casos) {
            mockMvc.perform(get(caso.ruta())
                            .header(HttpHeaders.AUTHORIZATION, tokenCon("MULTIMEDIA_GESTIONAR")))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------- 2. La ficha publica sigue completa ----------

    @Test
    void anonimo_hijoDeUnaPublicada_devuelve200() throws Exception {
        List<Caso> casos = casos(true);
        Assumptions.assumeFalse(casos.isEmpty(), "la base no tiene hijos de fichas publicadas");

        for (Caso caso : casos) {
            mockMvc.perform(get(caso.ruta()))
                    .andExpect(status().isOk());
        }

        assertFalse(casos.isEmpty());
    }

    // ---------- 3. Con el permiso si se ven ----------

    @Test
    void conEspecieConsultar_hijoDeUnBorrador_devuelve200() throws Exception {
        List<Caso> casos = casos(false);
        Assumptions.assumeFalse(casos.isEmpty(), "la base no tiene hijos de fichas sin publicar");

        for (Caso caso : casos) {
            mockMvc.perform(get(caso.ruta())
                            .header(HttpHeaders.AUTHORIZATION, tokenCon(ESPECIE_CONSULTAR)))
                    .andExpect(status().isOk());
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
     */
    @Test
    void aportesPorEspecieParte_elBorradorYUnIdInventadoDevuelvenListaVacia() throws Exception {
        Optional<Caso> deBorrador = buscar("aportes de una parte",
                especieParteComestibleRepository.findAll(),
                p -> p.getEspecie(),
                p -> "/api/aportes-nutricionales/especie-parte/" + p.getIdEspecieParte(),
                false);

        Assumptions.assumeTrue(deBorrador.isPresent(), "la base no tiene partes de fichas sin publicar");

        mockMvc.perform(get(deBorrador.get().ruta()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/aportes-nutricionales/especie-parte/" + ID_INEXISTENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    /** Con el permiso, la misma ruta si devuelve lo que haya. */
    @Test
    void aportesPorEspecieParte_conEspecieConsultar_noSeVacia() throws Exception {
        Optional<Caso> dePublicada = buscar("aportes de una parte",
                especieParteComestibleRepository.findAll(),
                p -> p.getEspecie(),
                p -> "/api/aportes-nutricionales/especie-parte/" + p.getIdEspecieParte(),
                true);

        Assumptions.assumeTrue(dePublicada.isPresent(), "la base no tiene partes de fichas publicadas");

        mockMvc.perform(get(dePublicada.get().ruta())
                        .header(HttpHeaders.AUTHORIZATION, tokenCon(ESPECIE_CONSULTAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ---------- La cadena de tres saltos, sembrada a mano ----------

    /**
     * Los datos sembrados cubren los recursos que cuelgan de la especie en uno
     * o dos saltos, pero la base de desarrollo no tiene preparaciones de
     * consumo, asi que la cadena mas larga --{@code imagen -> preparacion ->
     * parte comestible -> especie}-- se quedaba sin probar. Y es justo la que
     * mas lo necesita: es la unica con una forma distinta a las demas.
     * <p>
     * Por eso esta prueba siembra la cadena ella misma, colgando una
     * preparacion con su imagen y su video de dos partes comestibles que ya
     * existen: una de una ficha sin publicar y otra de una publicada. Las
     * filas viven dentro de la transaccion de la prueba y el rollback se las
     * lleva.
     */
    @Test
    void cadenaDeTresSaltos_imagenYVideoDePreparacion_respetanElEstadoDeLaFicha() throws Exception {
        EspecieParteComestible parteDeBorrador = primeraParte(false);
        EspecieParteComestible parteDePublicada = primeraParte(true);

        Assumptions.assumeTrue(parteDeBorrador != null && parteDePublicada != null,
                "la base no tiene partes comestibles de los dos estados");

        PreparacionConsumo prepDeBorrador = sembrarPreparacion(parteDeBorrador);
        PreparacionConsumo prepDePublicada = sembrarPreparacion(parteDePublicada);

        List<String> delBorrador = List.of(
                "/api/preparaciones-consumo/" + prepDeBorrador.getIdPreparacion(),
                "/api/imagenes-preparacion/" + sembrarImagen(prepDeBorrador).getIdImagenPreparacion(),
                "/api/videos-preparacion/" + sembrarVideo(prepDeBorrador).getIdVideoPreparacion(),
                "/api/imagenes-preparacion/preparacion/" + prepDeBorrador.getIdPreparacion(),
                "/api/videos-preparacion/preparacion/" + prepDeBorrador.getIdPreparacion());

        List<String> deLaPublicada = List.of(
                "/api/preparaciones-consumo/" + prepDePublicada.getIdPreparacion(),
                "/api/imagenes-preparacion/" + sembrarImagen(prepDePublicada).getIdImagenPreparacion(),
                "/api/videos-preparacion/" + sembrarVideo(prepDePublicada).getIdVideoPreparacion(),
                "/api/imagenes-preparacion/preparacion/" + prepDePublicada.getIdPreparacion(),
                "/api/videos-preparacion/preparacion/" + prepDePublicada.getIdPreparacion());

        for (String ruta : delBorrador) {
            mockMvc.perform(get(ruta))
                    .andExpect(status().isNotFound());

            mockMvc.perform(get(ruta)
                            .header(HttpHeaders.AUTHORIZATION, tokenCon(ESPECIE_CONSULTAR)))
                    .andExpect(status().isOk());
        }

        for (String ruta : deLaPublicada) {
            mockMvc.perform(get(ruta))
                    .andExpect(status().isOk());
        }
    }

    private EspecieParteComestible primeraParte(boolean dePublicada) {
        return especieParteComestibleRepository.findAll().stream()
                .filter(parte -> PUBLICADA.test(parte.getEspecie()) == dePublicada)
                .findFirst()
                .orElse(null);
    }

    private PreparacionConsumo sembrarPreparacion(EspecieParteComestible parte) {
        return preparacionConsumoRepository.saveAndFlush(PreparacionConsumo.builder()
                .especieParteComestible(parte)
                .nombre("Preparación de prueba " + parte.getIdEspecieParte())
                .tipoConsumo(TipoConsumo.CRUDO)
                .procedimiento("Procedimiento de prueba.")
                .build());
    }

    private ImagenPreparacion sembrarImagen(PreparacionConsumo preparacion) {
        return imagenPreparacionRepository.saveAndFlush(ImagenPreparacion.builder()
                .preparacion(preparacion)
                .urlImagen("https://example.invalid/prueba.jpg")
                .textoAlternativo("Imagen de prueba")
                .build());
    }

    private VideoPreparacion sembrarVideo(PreparacionConsumo preparacion) {
        return videoPreparacionRepository.saveAndFlush(VideoPreparacion.builder()
                .preparacion(preparacion)
                .titulo("Video de prueba")
                .urlVideo("https://example.invalid/prueba.mp4")
                .build());
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
