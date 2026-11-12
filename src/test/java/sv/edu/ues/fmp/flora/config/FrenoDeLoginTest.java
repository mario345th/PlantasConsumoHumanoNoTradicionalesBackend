package sv.edu.ues.fmp.flora.config;

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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import sv.edu.ues.fmp.flora.entity.Usuario;
import sv.edu.ues.fmp.flora.repository.UsuarioRepository;
import sv.edu.ues.fmp.flora.service.ControlDeIntentosDeLogin;
import sv.edu.ues.fmp.flora.service.ControlDeIntentosDeLogin.Cuenta;
import sv.edu.ues.fmp.flora.service.EmailService;

/**
 * Pruebas del freno a la fuerza bruta vistas desde HTTP, y de que el login
 * viejo siga funcionando aunque este deprecado.
 *
 * <h2>Cada prueba arranca con el contador limpio</h2>
 * El freno es un singleton con un mapa en memoria que vive mientras viva el
 * contexto de Spring, asi que lo que una prueba acumula lo verian las
 * siguientes: sin limpiar, dejar bloqueado un identificador en una prueba
 * haria fallar con 429 a la de al lado, y el resultado dependeria del orden en
 * que JUnit decidiera ejecutarlas. El {@code @BeforeEach} borra los
 * identificadores que usa la clase con {@code registrarExito}, que es la
 * operacion publica que reinicia el contador.
 *
 * <h2>El usuario con clave conocida lo siembra la propia prueba</h2>
 * Comprobar que el bloqueo responde 429 incluso con la clave buena, y que un
 * exito reinicia el contador, pide un usuario con clave conocida. Antes se
 * tomaba {@code admin.demo} de la migracion {@code V9} y, cuando no cuadraba
 * --base recien creada, o recargada como hizo {@code V14} con el catalogo--,
 * las cinco pruebas que lo necesitan se saltaban con
 * {@code Assumptions.assumeTrue}: cinco pruebas menos, dos de ellas la red de
 * SEG-H07, dentro de un BUILD SUCCESS que no delataba nada. Es el mismo
 * problema que {@link SiembraDeFichas} resolvio para las de visibilidad.
 * <p>
 * Ahora cada prueba siembra su propio usuario en el {@code @BeforeEach}, con
 * la clave hasheada por el mismo {@link PasswordEncoder} de la aplicacion: las
 * nueve corren siempre, con los datos que haya en la base. La siembra vive en
 * la transaccion de la prueba y el rollback se la lleva, asi que no queda
 * residuo; el nombre de usuario y el correo llevan un numero de secuencia
 * porque son unicos en la base y dos siembras chocarian entre si.
 * <p>
 * De paso el contador queda mas aislado que antes: cada prueba estrena
 * {@code id_usuario}, asi que no hay forma de que lo que una acumule lo vea la
 * siguiente.
 */
@SpringBootTest
@AutoConfigureMockMvc
// Los logins que si aciertan escriben ultimo_acceso; el rollback deja la base
// como estaba.
@Transactional
class FrenoDeLoginTest {

    private static final String LOGIN_NUEVO = "/api/auth/login";
    private static final String LOGIN_VIEJO = "/api/usuarios/login";

    /** El default de app.login.max-intentos, que no se declara en properties. */
    private static final int MAX_INTENTOS = 5;

    /** La clave que la prueba le pone al usuario que siembra. */
    private static final String CLAVE_SEMBRADA = "Freno2026#";

    /**
     * Distingue el usuario de una prueba del de la siguiente: el correo y el
     * nombre de usuario son unicos en la base, y los indices no entienden de
     * transacciones.
     */
    private static final AtomicLong SECUENCIA = new AtomicLong();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ControlDeIntentosDeLogin controlDeIntentosDeLogin;

    @MockitoBean
    private EmailService emailService;

    /**
     * Identificadores inventados que usa la clase, para dejarlos limpios antes
     * de cada prueba.
     * <p>
     * Ninguno existe en la base, asi que el freno los cuenta como texto sin
     * resolver y se limpian con {@link Cuenta#noResuelta(String)}. El usuario
     * sembrado no esta en esta lista precisamente porque si existe: sus
     * intentos van por id y se limpian abajo, con
     * {@link Cuenta#deUsuario(Long)}.
     */
    private static final List<String> IDENTIFICADORES_INVENTADOS = List.of(
            "no.existe.freno.auth",
            "no.existe.freno.viejo",
            "no.existe.freno.compartido",
            "no.existe.freno.aislado",
            "no.existe.freno.vecino");

    /**
     * El usuario de esta prueba, con {@link #CLAVE_SEMBRADA} por clave. Lo
     * siembra {@link #sembrarUsuarioYLimpiarContadores()} y el rollback de
     * {@code @Transactional} se lo lleva al terminar.
     */
    private Usuario usuario;

    @BeforeEach
    void sembrarUsuarioYLimpiarContadores() {
        long n = SECUENCIA.incrementAndGet();

        // saveAndFlush y no save: la peticion que viene despues resuelve la
        // cuenta con una consulta, y sin el flush la insercion seguiria en la
        // cola de Hibernate.
        usuario = usuarioRepository.saveAndFlush(Usuario.builder()
                .nombres("Freno")
                .apellidos("De Login")
                .correo("freno.login." + n + "@prueba.local")
                .nombreUsuario("freno.login." + n)
                // El mismo encoder de la aplicacion, para que la clave en
                // texto plano de arriba sea de verdad la de este usuario.
                .claveHash(passwordEncoder.encode(CLAVE_SEMBRADA))
                .activo(true)
                .build());

        IDENTIFICADORES_INVENTADOS.forEach(
                identificador -> controlDeIntentosDeLogin.registrarExito(
                        Cuenta.noResuelta(identificador)));

        // Una sola llamada basta para el usuario sembrado, aunque las pruebas
        // entren unas por el nombre de usuario y otras por el correo: desde
        // SEG-H07 las dos formas cuentan contra el mismo id.
        controlDeIntentosDeLogin.registrarExito(Cuenta.deUsuario(usuario.getIdUsuario()));
    }

    private static String cuerpo(String usuario, String clave) {
        return "{\"usuario\":\"" + usuario + "\",\"clave\":\"" + clave + "\"}";
    }

    private int intentar(String ruta, String usuario, String clave) throws Exception {
        return mockMvc.perform(post(ruta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(usuario, clave)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    /** Agota el limite con claves equivocadas; cada intento tiene que dar 401. */
    private void agotarIntentos(String ruta, String usuario) throws Exception {
        for (int i = 1; i <= MAX_INTENTOS; i++) {
            int estado = intentar(ruta, usuario, "ClaveEquivocada" + i + "#");
            org.junit.jupiter.api.Assertions.assertEquals(401, estado,
                    "el intento " + i + " deberia ser 401, no " + estado);
        }
    }

    /** Las dos formas de escribir la cuenta sembrada; las dos valen para entrar. */
    private String nombreDeUsuario() {
        return usuario.getNombreUsuario();
    }

    private String correo() {
        return usuario.getCorreo();
    }

    // ---------- El bloqueo ----------

    @Test
    void loginNuevo_trasAgotarLosIntentos_devuelve429ConElFormatoDeErrorDelProyecto() throws Exception {
        String usuario = "no.existe.freno.auth";
        agotarIntentos(LOGIN_NUEVO, usuario);

        mockMvc.perform(post(LOGIN_NUEVO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(usuario, "OtraMas1#")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.estado").value(429))
                .andExpect(jsonPath("$.error").value("Demasiadas peticiones"))
                .andExpect(jsonPath("$.ruta").value(LOGIN_NUEVO))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.mensaje").exists())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void loginViejo_trasAgotarLosIntentos_tambienDevuelve429() throws Exception {
        String usuario = "no.existe.freno.viejo";
        agotarIntentos(LOGIN_VIEJO, usuario);

        mockMvc.perform(post(LOGIN_VIEJO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(usuario, "OtraMas1#")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.ruta").value(LOGIN_VIEJO));
    }

    /**
     * La razon de aplicar el freno en {@code UsuarioService.iniciarSesion} y no
     * en cada controlador: los intentos de los dos endpoints suman al mismo
     * contador, asi que no se puede esquivar el limite alternando entre ellos.
     */
    @Test
    void losDosLoginsCompartenElContador() throws Exception {
        String usuario = "no.existe.freno.compartido";

        for (int i = 0; i < 3; i++) {
            org.junit.jupiter.api.Assertions.assertEquals(401,
                    intentar(LOGIN_NUEVO, usuario, "Equivocada" + i + "#"));
        }
        for (int i = 0; i < 2; i++) {
            org.junit.jupiter.api.Assertions.assertEquals(401,
                    intentar(LOGIN_VIEJO, usuario, "Equivocada" + i + "#"));
        }

        // Cinco fallos repartidos entre los dos endpoints bastan: el siguiente
        // intento, por cualquiera de los dos, ya esta bloqueado.
        mockMvc.perform(post(LOGIN_NUEVO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(usuario, "Equivocada9#")))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(post(LOGIN_VIEJO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(usuario, "Equivocada9#")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void elBloqueoDeUnUsuarioNoAfectaALosDemas() throws Exception {
        agotarIntentos(LOGIN_NUEVO, "no.existe.freno.aislado");

        // Otro identificador sigue recibiendo el 401 de siempre, no un 429.
        mockMvc.perform(post(LOGIN_NUEVO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("no.existe.freno.vecino", "LaQueSea1#")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Usuario o clave incorrectos"));
    }

    /**
     * Lo que hace util al bloqueo: mientras dura, ni la clave correcta entra.
     * Si respondiera 200, el enfriamiento solo estorbaria al que se equivoca y
     * no frenaria al que acierta probando.
     */
    @Test
    void duranteElBloqueo_laClaveCorrectaTambienRecibe429() throws Exception {
        agotarIntentos(LOGIN_NUEVO, nombreDeUsuario());

        mockMvc.perform(post(LOGIN_NUEVO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(nombreDeUsuario(), CLAVE_SEMBRADA)))
                .andExpect(status().isTooManyRequests());
    }

    /**
     * SEG-H07, el hallazgo: el bloqueo no se esquiva cambiando de forma de
     * escribir la misma cuenta.
     *
     * <h2>Que media antes y que mide ahora</h2>
     * El contador iba por el texto que se escribia, asi que el nombre de
     * usuario y el correo eran dos contadores distintos: agotados los cinco
     * intentos con el nombre de usuario, el correo de la MISMA cuenta tenia
     * otros cinco y la respuesta era el 401 de credenciales. Ahora el login
     * resuelve la cuenta antes de contar, los dos caminos llegan al mismo
     * {@code id_usuario} y el segundo intento ya sale bloqueado.
     * <p>
     * El segundo intento va con la clave CORRECTA a proposito: si respondiera
     * 200 el bloqueo no serviria de nada, y ademas demuestra que el 429 no es
     * un 401 disfrazado.
     */
    @Test
    void elBloqueoPorNombreDeUsuario_tambienAlcanzaAlCorreoDeLaMismaCuenta() throws Exception {
        agotarIntentos(LOGIN_NUEVO, nombreDeUsuario());

        mockMvc.perform(post(LOGIN_NUEVO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(correo(), CLAVE_SEMBRADA)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.estado").value(429));
    }

    /** Y al reves: agotarlos con el correo tambien bloquea el nombre de usuario. */
    @Test
    void elBloqueoPorCorreo_tambienAlcanzaAlNombreDeUsuario() throws Exception {
        agotarIntentos(LOGIN_NUEVO, correo());

        mockMvc.perform(post(LOGIN_NUEVO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(nombreDeUsuario(), CLAVE_SEMBRADA)))
                .andExpect(status().isTooManyRequests());
    }

    /**
     * Quien se equivoco unas cuantas veces y luego acerto no arrastra nada: el
     * contador vuelve a cero y le queda el limite completo.
     */
    @Test
    void unExitoAntesDelLimite_reiniciaElContador() throws Exception {
        for (int i = 0; i < MAX_INTENTOS - 1; i++) {
            org.junit.jupiter.api.Assertions.assertEquals(401,
                    intentar(LOGIN_NUEVO, correo(), "Equivocada" + i + "#"));
        }

        mockMvc.perform(post(LOGIN_NUEVO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(correo(), CLAVE_SEMBRADA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());

        // Con el contador reiniciado vuelve a tener los cinco intentos, asi que
        // estos cuatro fallos no bloquean.
        for (int i = 0; i < MAX_INTENTOS - 1; i++) {
            org.junit.jupiter.api.Assertions.assertEquals(401,
                    intentar(LOGIN_NUEVO, correo(), "OtraEquivocada" + i + "#"));
        }
    }

    // ---------- El login viejo sigue vivo ----------

    /**
     * Deprecado no es roto: mientras el frontend migre tiene que seguir
     * devolviendo el usuario, sin token, exactamente como antes.
     */
    @Test
    void loginViejo_deprecadoPeroNoRoto() throws Exception {
        mockMvc.perform(post(LOGIN_VIEJO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(nombreDeUsuario(), CLAVE_SEMBRADA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreUsuario").value(nombreDeUsuario()))
                // Sigue sin emitir token: es lo que lo hace insuficiente y la
                // razon de que haya que retirarlo.
                .andExpect(jsonPath("$.token").doesNotExist())
                // Y sigue sin devolver el hash de la clave.
                .andExpect(jsonPath("$.claveHash").doesNotExist());
    }
}
