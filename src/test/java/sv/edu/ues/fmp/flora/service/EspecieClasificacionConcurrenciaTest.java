package sv.edu.ues.fmp.flora.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import sv.edu.ues.fmp.flora.dto.request.EspecieRequest;
import sv.edu.ues.fmp.flora.dto.request.TaxonomiaRequest;
import sv.edu.ues.fmp.flora.dto.response.UsuarioResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * La carrera del hallazgo de QA sobre la clasificacion repetida, reproducida
 * contra {@code uk_taxonomia} tal como esta hoy en la base:
 * {@code UNIQUE NULLS NOT DISTINCT (reino, genero, especie_taxonomica,
 * subespecie)}. La restriccion NO se modifico; lo que se arreglo fue el
 * codigo, y esto lo comprueba.
 *
 * <h2>Que ventana se abre, y por que no basta con lanzar peticiones a la vez</h2>
 * {@code EspecieServiceImpl.crear()} consulta duplicados
 * ({@code validarClasificacionUnica}) y despues inserta. Entre esas dos
 * sentencias cabe otra transaccion. Lanzar N peticiones simultaneas no
 * garantiza caer en esa ventana: si una alcanza a confirmar antes de que las
 * demas consulten, las demas reciben el 409 de la comprobacion previa y la
 * carrera no se prueba.
 * <p>
 * Por eso el ganador corre dentro de una transaccion que esta prueba mantiene
 * abierta a proposito ({@link TransactionTemplate} mas un cerrojo): sus filas
 * ya estan escritas pero sin confirmar, de modo que
 * <ul>
 *   <li>la consulta de duplicados de las otras peticiones no las ve --READ
 *       COMMITTED-- y todas pasan la comprobacion previa, y</li>
 *   <li>su INSERT choca contra la entrada no confirmada del indice y se queda
 *       esperando.</li>
 * </ul>
 * Que esten esperando no se supone: se verifica en {@code pg_stat_activity}
 * antes de soltar al ganador ({@link #bloqueadasEnTaxonomia()}).
 *
 * <h2>Lo que se afirma</h2>
 * <ol>
 *   <li>Las tres peticiones perdedoras quedan esperando en el indice: la
 *       restriccion serializa las inserciones simultaneas.</li>
 *   <li>Al confirmar el ganador, las tres reciben <strong>409</strong> con el
 *       mensaje que nombra la clasificacion, no el 409 generico del
 *       {@code GlobalExceptionHandler} ni un 500.</li>
 *   <li>Queda UNA taxonomia y UNA especie. Ni tres duplicados --lo que
 *       reporto QA-- ni fichas a medias de las perdedoras.</li>
 * </ol>
 *
 * <h2>Datos</h2>
 * Corre contra la base de desarrollo, como el resto de las pruebas de
 * {@code @SpringBootTest} del proyecto, y no puede ser {@code @Transactional}:
 * el rollback de la prueba no serviria porque cada hilo necesita su propia
 * transaccion. Por eso usa un genero inventado ({@code Zztestus}), que no
 * existe en el catalogo, y borra lo que crea en {@link #limpiar()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EspecieClasificacionConcurrenciaTest {

    /** Genero inventado: nada del catalogo real empieza por "Zztestus". */
    private static final String GENERO = "Zztestus";
    private static final String ESPECIE_TAXONOMICA = "concurrencia";

    /** Las que compiten por la misma clasificacion, con nombres distintos. */
    private static final String NOMBRE_GANADOR = "Zztestus concurrencia uno";
    private static final List<String> NOMBRES_PERDEDORES = List.of(
            "Zztestus concurrencia dos",
            "Zztestus concurrencia tres",
            "Zztestus concurrencia cuatro");

    /** El id del usuario del token tiene que existir: crear() lo busca. */
    private static final long ID_ADMIN = 1L;

    private static final int ESPERA_SEGUNDOS = 20;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EspecieService especieService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private EmailService emailService;

    @AfterEach
    void limpiar() {
        jdbcTemplate.update(
                "DELETE FROM especie WHERE lower(nombre_cientifico) LIKE 'zztestus %'");
        jdbcTemplate.update("DELETE FROM taxonomia WHERE genero = ?", GENERO);
    }

    // ---------- La carrera ----------

    @Test
    void tresPeticionesSimultaneas_conLaMismaClasificacion_noDejanDuplicados() throws Exception {
        CountDownLatch insertado = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);

        ExecutorService hilos = Executors.newFixedThreadPool(1 + NOMBRES_PERDEDORES.size());
        try {
            // 1. El ganador inserta y se queda con la transaccion abierta.
            Future<?> ganador = hilos.submit(() -> {
                autenticarEnEsteHilo();
                new TransactionTemplate(transactionManager).execute(estado -> {
                    especieService.crear(peticion(NOMBRE_GANADOR, ESPECIE_TAXONOMICA, null));
                    insertado.countDown();
                    esperar(liberar);
                    return null;
                });
                return null;
            });

            assertTrue(insertado.await(ESPERA_SEGUNDOS, TimeUnit.SECONDS),
                    "el hilo ganador no llego a insertar");

            // 2. Las tres perdedoras entran con la fila del ganador escrita
            //    pero sin confirmar.
            List<Future<MvcResult>> perdedoras = new ArrayList<>();
            for (String nombre : NOMBRES_PERDEDORES) {
                perdedoras.add(hilos.submit(peticionHttp(nombre)));
            }

            // 3. Y se comprueba que las tres esperan en el indice.
            int bloqueadas = esperarBloqueadas(NOMBRES_PERDEDORES.size());
            assertEquals(NOMBRES_PERDEDORES.size(), bloqueadas,
                    "las peticiones no quedaron esperando en uk_taxonomia; sin eso la "
                            + "carrera no se reprodujo y el resto de la prueba no significa nada");

            // 4. Confirma el ganador: ahi fallan las tres.
            liberar.countDown();
            ganador.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);

            for (Future<MvcResult> perdedora : perdedoras) {
                MvcResult resultado = perdedora.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
                String cuerpo = resultado.getResponse().getContentAsString();

                assertEquals(409, resultado.getResponse().getStatus(),
                        "la peticion que perdio la carrera no respondio 409: " + cuerpo);

                // El mensaje nombra la clasificacion. El 409 generico de la
                // DataIntegrityViolationException ("La operacion viola una
                // restriccion de la base de datos") no la nombra, asi que esta
                // comparacion distingue el catch de uk_taxonomia del manejador
                // de ultimo recurso. Se busca una subcadena sin acentos a
                // proposito, para no depender de la codificacion del archivo.
                assertTrue(cuerpo.contains(GENERO + " " + ESPECIE_TAXONOMICA),
                        "el 409 no explica que clasificacion choco: " + cuerpo);
            }
        } finally {
            liberar.countDown();
            hilos.shutdownNow();
        }

        // 5. Una taxonomia, una especie: ningun duplicado y ninguna ficha a
        //    medias de las tres que perdieron.
        assertEquals(1, taxonomiasDePrueba(),
                "la clasificacion quedo repetida en taxonomia");
        assertEquals(1, especiesDePrueba(),
                "se crearon especies de las peticiones que perdieron la carrera");
        for (String nombre : NOMBRES_PERDEDORES) {
            assertEquals(0, especiesConNombre(nombre),
                    "quedo creada la especie de una peticion que respondio 409: " + nombre);
        }
    }

    // ---------- La otra forma de colar tres filas iguales ----------

    /**
     * {@code NULLS NOT DISTINCT} iguala dos NULL, pero no iguala NULL con la
     * cadena vacia ni con una de espacios: para el indice esas son tres claves
     * distintas y las tres filas caben. Lo que las iguala es la normalizacion
     * del servicio ({@code Textos.normalizar} devuelve null cuando no queda
     * nada), y es lo que se comprueba aqui, porque es la via por la que la
     * misma clasificacion podria seguir entrando tres veces sin que la
     * restriccion se entere.
     */
    @Test
    void subespecieVaciaOEnBlanco_cuentaComoSinSubespecie() throws Exception {
        String conNull = crearPorHttp("Zztestus vacios uno", "vacios", null);
        assertTrue(conNull.startsWith("201"), "la primera no se creo: " + conNull);

        String conVacia = crearPorHttp("Zztestus vacios dos", "vacios", "");
        assertTrue(conVacia.startsWith("409"),
                "la subespecie vacia creo una segunda clasificacion igual: " + conVacia);
        assertTrue(conVacia.contains(GENERO + " vacios"), conVacia);

        String conEspacios = crearPorHttp("Zztestus vacios tres", "vacios", "   ");
        assertTrue(conEspacios.startsWith("409"),
                "la subespecie en blanco creo una tercera clasificacion igual: " + conEspacios);
        assertTrue(conEspacios.contains(GENERO + " vacios"), conEspacios);

        assertEquals(1, taxonomiasDePrueba(), "quedo mas de una clasificacion Zztestus");
    }

    // ---------- Apoyo ----------

    private Callable<MvcResult> peticionHttp(String nombreCientifico) {
        return () -> mockMvc.perform(post("/api/especies")
                        .header("Authorization", tokenDeCreacion())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(nombreCientifico, ESPECIE_TAXONOMICA, null)))
                .andReturn();
    }

    /** Devuelve "estado cuerpo" para poder afirmar sobre los dos. */
    private String crearPorHttp(String nombreCientifico, String especieTaxonomica,
            String subespecie) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/especies")
                        .header("Authorization", tokenDeCreacion())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(nombreCientifico, especieTaxonomica, subespecie)))
                .andReturn();

        return resultado.getResponse().getStatus() + " "
                + resultado.getResponse().getContentAsString();
    }

    private String cuerpo(String nombreCientifico, String especieTaxonomica, String subespecie) {
        String campoSubespecie = (subespecie == null)
                ? ""
                : ",\"subespecie\":\"" + subespecie + "\"";

        return "{\"nombreCientifico\":\"" + nombreCientifico + "\","
                + "\"descripcion\":\"Registro de prueba de concurrencia; se borra al terminar.\","
                + "\"taxonomia\":{\"genero\":\"" + GENERO + "\","
                + "\"especieTaxonomica\":\"" + especieTaxonomica + "\"" + campoSubespecie + "}}";
    }

    private EspecieRequest peticion(String nombreCientifico, String especieTaxonomica,
            String subespecie) {
        TaxonomiaRequest clasificacion = new TaxonomiaRequest();
        clasificacion.setGenero(GENERO);
        clasificacion.setEspecieTaxonomica(especieTaxonomica);
        clasificacion.setSubespecie(subespecie);

        EspecieRequest peticion = new EspecieRequest();
        peticion.setNombreCientifico(nombreCientifico);
        peticion.setDescripcion("Registro de prueba de concurrencia; se borra al terminar.");
        peticion.setTaxonomia(clasificacion);
        return peticion;
    }

    /**
     * El token lleva el id de un usuario que existe porque {@code crear()}
     * busca al creador por el {@code sub} y responde 404 si no esta.
     */
    private String tokenDeCreacion() {
        UsuarioResponse usuario = UsuarioResponse.builder()
                .id(ID_ADMIN)
                .nombres("Admin")
                .apellidos("Prueba")
                .correo("admin@prueba.local")
                .nombreUsuario("admin")
                .activo(true)
                .build();

        return "Bearer " + jwtService
                .emitir(usuario, List.of("ADMINISTRADOR"), List.of("ESPECIE_CREAR"))
                .token();
    }

    /**
     * El hilo del ganador llama al servicio directamente, sin pasar por el
     * filtro de Spring Security, asi que le pone a mano la autenticacion que
     * {@code UsuarioAutenticado} va a leer del hilo.
     */
    private void autenticarEnEsteHilo() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        String.valueOf(ID_ADMIN), null, List.of()));
    }

    /**
     * Cuantas sesiones de la base estan esperando a que otra transaccion
     * termine para poder insertar en taxonomia. Es exactamente la espera que
     * provoca una entrada no confirmada de un indice unico: PostgreSQL la
     * reporta como {@code Lock / transactionid}.
     */
    private int bloqueadasEnTaxonomia() {
        Integer cuantas = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_stat_activity "
                        + "WHERE datname = current_database() "
                        + "  AND wait_event_type = 'Lock' "
                        + "  AND wait_event = 'transactionid' "
                        + "  AND query ILIKE '%taxonomia%'",
                Integer.class);
        return (cuantas == null) ? 0 : cuantas;
    }

    private int esperarBloqueadas(int cuantas) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(ESPERA_SEGUNDOS);
        int vistas = 0;
        while (System.nanoTime() < limite) {
            vistas = bloqueadasEnTaxonomia();
            if (vistas >= cuantas) {
                return vistas;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        return vistas;
    }

    private int taxonomiasDePrueba() {
        Integer cuantas = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM taxonomia WHERE genero = ?", Integer.class, GENERO);
        return (cuantas == null) ? 0 : cuantas;
    }

    private int especiesDePrueba() {
        Integer cuantas = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM especie WHERE lower(nombre_cientifico) LIKE 'zztestus %'",
                Integer.class);
        return (cuantas == null) ? 0 : cuantas;
    }

    private int especiesConNombre(String nombreCientifico) {
        Integer cuantas = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM especie WHERE lower(nombre_cientifico) = lower(?)",
                Integer.class, nombreCientifico);
        return (cuantas == null) ? 0 : cuantas;
    }

    private void esperar(CountDownLatch cerrojo) {
        try {
            if (!cerrojo.await(ESPERA_SEGUNDOS, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "nadie solto la transaccion del ganador en " + ESPERA_SEGUNDOS + " s");
            }
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupcion);
        }
    }
}
