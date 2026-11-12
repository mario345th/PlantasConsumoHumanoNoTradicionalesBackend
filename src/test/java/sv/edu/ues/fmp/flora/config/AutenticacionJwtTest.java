package sv.edu.ues.fmp.flora.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
// En Spring Boot 4 las autoconfiguraciones de prueba se repartieron por
// modulo: @AutoConfigureMockMvc ya no esta en
// org.springframework.boot.test.autoconfigure.web.servlet sino aqui, en el
// modulo spring-boot-webmvc-test.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import jakarta.persistence.EntityManager;
import sv.edu.ues.fmp.flora.dto.response.RolResponse;
import sv.edu.ues.fmp.flora.dto.response.UsuarioResponse;
import sv.edu.ues.fmp.flora.entity.Rol;
import sv.edu.ues.fmp.flora.entity.Usuario;
import sv.edu.ues.fmp.flora.entity.UsuarioRol;
import sv.edu.ues.fmp.flora.entity.enums.UsuarioRolId;
import sv.edu.ues.fmp.flora.service.EmailService;
import sv.edu.ues.fmp.flora.service.JwtService;
import sv.edu.ues.fmp.flora.service.UsuarioRolService;

/**
 * Pruebas de la autenticacion por token: que el token lleve lo que debe, que
 * sus claims se traduzcan a las autoridades correctas, y que la matriz de
 * rutas de {@link SecurityConfig} deje pasar lo publico y cierre lo demas.
 *
 * <h2>Por que no se prueba el login con usuario y clave</h2>
 * Casi todas las pruebas emiten el token directamente con {@link JwtService},
 * sin pasar por {@code POST /api/auth/login}. Para la autenticacion da lo
 * mismo: el filtro de Spring Security solo mira la firma y la vigencia del
 * token, no consulta la base para saber si el usuario existe. Un token bien
 * firmado entra aunque su {@code sub} no corresponda a ninguna fila. El
 * recorrido completo con clave en texto plano lo cubre
 * {@link FrenoDeLoginTest}, que siembra su propio usuario.
 *
 * <h2>Las dos pruebas que si necesitan filas las siembran</h2>
 * {@code cambioDeClave_sobreSuPropioId...} necesita un usuario y
 * {@code rolesDesactivados_noViajanEnElToken} uno con un rol dado de baja.
 * Las dos buscaban el caso entre lo que hubiera en la base y, cuando no lo
 * encontraban, se saltaban con {@code Assumptions.assumeTrue}: en una base
 * limpia eran dos pruebas menos dentro de un BUILD SUCCESS que no delataba
 * nada, el mismo problema que {@link SiembraDeFichas} resolvio para las de
 * visibilidad. Ahora cada una siembra lo que necesita con el
 * {@link EntityManager} y lleva {@code @Transactional} para que el rollback se
 * lo lleve; el resto de la clase sigue sin tocar la base, asi que la
 * anotacion va en esos dos metodos y no en la clase.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AutenticacionJwtTest {

    /** Ruta protegida cualquiera, sirve para comprobar que exige token. */
    private static final String RUTA_PROTEGIDA = "/api/usuarios";

    /**
     * Ruta del catalogo que la matriz deja abierta en GET.
     * <p>
     * Es {@code /publicadas} y no {@code /api/especies} porque ese ultimo dejo
     * de ser publico: devolvia tambien las fichas en BORRADOR. Lo que queda
     * abierto es el listado del catalogo, que solo trae PUBLICADA.
     */
    private static final String RUTA_PUBLICA = "/api/especies/publicadas";

    /** Ruta del catalogo cuya escritura exige token; sirve para el 401 del filtro. */
    private static final String RUTA_ESCRITURA_CATALOGO = "/api/especies";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtAuthenticationConverter convertidorDeAutoridades;

    @Autowired
    private UsuarioRolService usuarioRolService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    /**
     * El contexto completo arrastra el envio de correos de la recuperacion de
     * clave. Ninguna prueba de aqui lo usa, pero se sustituye para que cargar
     * el contexto no dependa de que haya credenciales de correo configuradas.
     */
    @MockitoBean
    private EmailService emailService;

    private UsuarioResponse usuarioDePrueba() {
        return UsuarioResponse.builder()
                .id(999L)
                .nombres("Prueba")
                .apellidos("Automatizada")
                .correo("prueba.automatizada@local")
                .nombreUsuario("prueba.automatizada")
                .activo(true)
                .build();
    }

    private String tokenCon(List<String> roles, List<String> permisos) {
        return jwtService.emitir(usuarioDePrueba(), roles, permisos).token();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    // ---------- Siembra de las dos pruebas que necesitan filas ----------

    /**
     * Distingue una siembra de otra. El correo y el nombre de usuario son
     * unicos en la base, y el nombre del rol tambien: los indices no entienden
     * de transacciones, asi que dos siembras con el mismo nombre chocarian
     * entre si aunque las dos vayan a deshacerse.
     */
    private static final AtomicLong SECUENCIA = new AtomicLong();

    /**
     * Un usuario activo con la clave indicada, hasheada por el mismo
     * {@link PasswordEncoder} de la aplicacion.
     * <p>
     * Con {@code persist} y {@code flush} en vez de un repositorio por el
     * motivo que explica {@link SiembraDeFichas}: el {@code EntityManager} de
     * la transaccion es el mismo que usan los repositorios por debajo, asi que
     * la consulta del servicio ve lo sembrado, y basta una dependencia.
     */
    private Usuario sembrarUsuario(String clave) {
        long n = SECUENCIA.incrementAndGet();

        Usuario sembrado = Usuario.builder()
                .nombres("Prueba")
                .apellidos("Autenticacion")
                .correo("prueba.autenticacion." + n + "@prueba.local")
                .nombreUsuario("prueba.autenticacion." + n)
                .claveHash(passwordEncoder.encode(clave))
                .activo(true)
                .build();

        entityManager.persist(sembrado);
        entityManager.flush();

        return sembrado;
    }

    private Rol sembrarRol(boolean activo) {
        long n = SECUENCIA.incrementAndGet();

        Rol rol = Rol.builder()
                .nombre((activo ? "PRUEBA_VIGENTE_" : "PRUEBA_DE_BAJA_") + n)
                .descripcion("Rol sembrado por las pruebas de autenticación")
                .activo(activo)
                .build();

        entityManager.persist(rol);

        return rol;
    }

    /**
     * Asigna el rol al usuario escribiendo la fila de {@code usuario_rol} a
     * mano.
     * <p>
     * No pasa por {@code UsuarioRolService.asignar} a proposito: ese metodo
     * rechaza con 409 los roles desactivados --{@code "No se puede asignar el
     * rol X porque está desactivado"}-- y lo que esta prueba necesita es
     * justamente la fila que quedo de cuando el rol todavia estaba vigente,
     * que es el escenario real de un rol dado de baja despues de asignarlo.
     */
    private void asignar(Usuario usuario, Rol rol) {
        entityManager.persist(UsuarioRol.builder()
                .id(new UsuarioRolId(usuario.getIdUsuario(), rol.getIdRol()))
                .usuario(usuario)
                .rol(rol)
                .build());

        entityManager.flush();
    }

    // ---------- Contenido del token ----------

    @Test
    void tokenEmitido_llevaSubNombreUsuarioRolesPermisosIatYExp() {
        UsuarioResponse usuario = usuarioDePrueba();

        JwtService.TokenEmitido emitido = jwtService.emitir(
                usuario, List.of("ADMIN"), List.of("ESPECIE_CREAR"));

        Jwt token = jwtDecoder.decode(emitido.token());

        assertEquals("999", token.getSubject(), "sub debe ser el id del usuario");
        assertEquals("prueba.automatizada", token.getClaimAsString("nombreUsuario"));
        assertEquals(List.of("ADMIN"), token.getClaimAsStringList("roles"));
        assertEquals(List.of("ESPECIE_CREAR"), token.getClaimAsStringList("permisos"));
        assertNotNull(token.getIssuedAt(), "iat debe venir en el token");
        assertNotNull(token.getExpiresAt(), "exp debe venir en el token");
        assertTrue(token.getExpiresAt().isAfter(token.getIssuedAt()));
    }

    @Test
    void tokenEmitido_caducaSegunLaPropiedadDeExpiracion() {
        JwtService.TokenEmitido emitido = jwtService.emitir(
                usuarioDePrueba(), List.of(), List.of());

        Jwt token = jwtDecoder.decode(emitido.token());

        long minutos = Duration.between(token.getIssuedAt(), token.getExpiresAt()).toMinutes();
        assertEquals(60L, minutos, "la vigencia debe ser la de app.jwt.expiration-minutes");

        // expiraEn de la respuesta y exp del token tienen que ser el mismo
        // instante, o el cliente y el servidor no coincidirian en cuando
        // caduca la sesion.
        assertEquals(
                token.getExpiresAt().truncatedTo(ChronoUnit.SECONDS),
                emitido.expiraEn().atZone(java.time.ZoneId.systemDefault()).toInstant()
                        .truncatedTo(ChronoUnit.SECONDS));
    }

    @Test
    void tokenEmitido_noLlevaNadaDeLaClave() {
        String token = tokenCon(List.of("ADMIN"), List.of());
        Jwt decodificado = jwtDecoder.decode(token);

        assertFalse(decodificado.getClaims().containsKey("clave"));
        assertFalse(decodificado.getClaims().containsKey("claveHash"));
    }

    // ---------- Traduccion de claims a autoridades ----------

    /**
     * Ojo con {@code FACTOR_BEARER}: no sale de ningun claim nuestro. Lo
     * agrega Spring Security 7 por su cuenta para dejar constancia de con que
     * factor se autentico la peticion (es la base de su soporte de
     * autenticacion multifactor). Aparece en toda peticion con token, y la
     * prueba la lista a proposito para que, si una version futura la quita o
     * la renombra, el cambio se note aqui y no en un {@code @PreAuthorize}
     * que de pronto deje de cumplirse.
     */
    @Test
    void convertidor_poneRolePrefijoAlosRolesYDejaLosPermisosPelados() {
        String token = tokenCon(List.of("ADMIN", "INVESTIGADOR"), List.of("ESPECIE_CREAR"));

        assertEquals(
                List.of("ESPECIE_CREAR", "FACTOR_BEARER", "ROLE_ADMIN", "ROLE_INVESTIGADOR"),
                autoridadesDe(token));
    }

    @Test
    void convertidor_aceptaUnTokenSinClaimsDeAutoridades() {
        String token = tokenCon(List.of(), List.of());

        assertEquals(List.of("FACTOR_BEARER"), autoridadesDe(token),
                "un token sin roles ni permisos autentica, pero no concede ninguna autoridad propia");
    }

    private List<String> autoridadesDe(String token) {
        return convertidorDeAutoridades.convert(jwtDecoder.decode(token))
                .getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();
    }

    // ---------- Matriz de rutas ----------

    @Test
    void rutaProtegida_sinToken_devuelve401ConElFormatoDeErrorDelProyecto() throws Exception {
        mockMvc.perform(get(RUTA_PROTEGIDA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.estado").value(401))
                .andExpect(jsonPath("$.error").value("No autorizado"))
                .andExpect(jsonPath("$.ruta").value(RUTA_PROTEGIDA))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    /**
     * RUTA_PROTEGIDA es GET /api/usuarios, que desde que existe la
     * autorizacion por permiso exige USUARIO_GESTIONAR. El token lo lleva
     * porque lo que mide esta prueba es la autenticacion --que un token bien
     * firmado entra-- y no el permiso, que se prueba aparte en
     * {@link AutorizacionPorPermisoTest}.
     */
    @Test
    void rutaProtegida_conTokenValido_devuelve200() throws Exception {
        String token = tokenCon(List.of("ADMIN"), List.of("USUARIO_GESTIONAR"));

        mockMvc.perform(get(RUTA_PROTEGIDA)
                        .param("pagina", "0")
                        .param("tamanio", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void rutaProtegida_conTokenExpirado_devuelve401() throws Exception {
        mockMvc.perform(get(RUTA_PROTEGIDA)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenExpirado())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.estado").value(401));
    }

    @Test
    void rutaProtegida_conFirmaAlterada_devuelve401() throws Exception {
        String token = tokenCon(List.of("ADMIN"), List.of());

        mockMvc.perform(get(RUTA_PROTEGIDA)
                        .header(HttpHeaders.AUTHORIZATION, bearer(conLaFirmaAlterada(token))))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Cambia un caracter de la firma dejando cabecera y cuerpo intactos, para
     * que lo unico que falle sea la verificacion HMAC.
     * <p>
     * Toca el <em>primer</em> caracter de la firma y no el ultimo, que es lo
     * que parecia natural. La firma HMAC-SHA256 son 32 bytes, que en base64url
     * ocupan 43 caracteres: 43 por 6 son 258 bits para 256 reales, asi que los
     * dos ultimos bits del caracter final son relleno y el decodificador los
     * descarta. Cambiar ese ultimo caracter produce a veces exactamente los
     * mismos 32 bytes, el token sigue verificando y la prueba pasaba o fallaba
     * segun el token que tocara. El primer caracter si aporta sus 6 bits, asi
     * que alterarlo cambia los bytes siempre.
     */
    private static String conLaFirmaAlterada(String token) {
        int inicioDeLaFirma = token.lastIndexOf('.') + 1;
        char original = token.charAt(inicioDeLaFirma);

        return token.substring(0, inicioDeLaFirma)
                + (original == 'A' ? 'B' : 'A')
                + token.substring(inicioDeLaFirma + 1);
    }

    @Test
    void rutaProtegida_conCabeceraQueNiSiquieraEsUnJwt_devuelve401() throws Exception {
        mockMvc.perform(get(RUTA_PROTEGIDA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer esto-no-es-un-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void catalogoPublico_sigueAbiertoSinToken() throws Exception {
        mockMvc.perform(get(RUTA_PUBLICA))
                .andExpect(status().isOk());
    }

    @Test
    void contratoOpenApi_sigueAbiertoSinToken() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void loginDeAuth_esPublicoYRechazaCredencialesInvalidasCon401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"no.existe\",\"clave\":\"no-importa\"}"))
                .andExpect(status().isUnauthorized())
                // El 401 tiene que venir del servicio, no del filtro: si
                // viniera del filtro el mensaje seria el generico de falta de
                // token y la ruta no habria llegado nunca al controlador.
                .andExpect(jsonPath("$.mensaje").value("Usuario o clave incorrectos"));
    }

    /**
     * H5: bajo {@code /api/auth} lo publico es el login y nada mas.
     *
     * <h2>Que fijaba esto y que fija ahora</h2>
     * La matriz abria {@code /api/auth/**} con un comodin, asi que
     * cualquier endpoint que naciera bajo ese prefijo --{@code GET
     * /api/auth/me}, que devuelve la identidad de quien llama, o un refresh,
     * que emite credenciales-- quedaba {@code permitAll} sin que nadie lo
     * decidiera. Al acotarlo a {@code /api/auth/login} eso se arreglo, pero
     * ninguna prueba lo sujetaba: devolver el comodin no rompia nada y el
     * agujero volveria en silencio.
     *
     * <h2>Por que se mide con rutas que todavia no existen</h2>
     * Dos son las que el {@code AuthController} tiene anotadas como
     * pendientes --{@code me} y el refresh--, una es el cierre de sesion que
     * pediria un token de verdad, y la cuarta es el prefijo pelado.
     * Que no haya controlador no estorba, al contrario: la matriz de
     * autorizacion se evalua en el filtro, antes de que Spring MVC busque un
     * handler, asi que sin token la respuesta es el 401 de la matriz. El
     * segundo tramo --con un token valido la misma ruta responde 404-- es lo
     * que demuestra que el 401 lo puso la exigencia de credencial y no la
     * ruta inexistente: con el comodin de vuelta, las dos respuestas serian
     * 404 y la prueba se caeria en la primera.
     * <p>
     * Si algun dia uno de estos endpoints se implementa, el 404 pasara a ser
     * la respuesta que le toque y habra que ajustar la lista; lo que no
     * cambia es que sin token tiene que seguir dando 401.
     */
    @ParameterizedTest(name = "{0} no es publica")
    @ValueSource(strings = {
            "/api/auth",
            "/api/auth/me",
            "/api/auth/refresh",
            "/api/auth/logout"
    })
    void bajoApiAuth_soloElLoginEsPublico(String hermanaDelLogin) throws Exception {
        mockMvc.perform(get(hermanaDelLogin))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.estado").value(401))
                .andExpect(jsonPath("$.ruta").value(hermanaDelLogin));

        mockMvc.perform(get(hermanaDelLogin)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenCon(List.of(), List.of()))))
                .andExpect(status().isNotFound());
    }

    /**
     * La otra mitad del recorte: lo que quedo publico es la <em>ruta</em> del
     * login, no solo su {@code POST}.
     * <p>
     * Un {@code GET} al login responde 405 --el {@code @PostMapping} del
     * controlador no admite el verbo-- y no 401. Es lo correcto en una ruta
     * que no pide identidad, y es lo que distingue el recorte por ruta de un
     * recorte por metodo: si alguien acotara el {@code permitAll} a
     * {@code POST /api/auth/login}, esta respuesta pasaria a ser un 401 y la
     * prueba lo cazaria.
     */
    @Test
    void loginDeAuth_conOtroVerbo_responde405YNo401() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed());
    }

    /**
     * SEG-H01: una cabecera {@code Authorization} que no sirve no tumba una
     * ruta publica.
     *
     * <h2>Que fallaba</h2>
     * El filtro del token corre antes de la matriz de rutas, asi que veia la
     * cabecera, intentaba autenticarla y cortaba con 401 sin que el
     * controlador llegara a atender. Con esta cabecera no hace falta ni un
     * token mal firmado: el resolvedor por omision exige que lo que sigue a
     * "Bearer " encaje con {@code [a-zA-Z0-9-._~+/]+=*}, y los signos de
     * admiracion no encajan, asi que la rechazaba como token mal formado.
     * Resultado: quien tenia el token corrupto no podia ni volver a iniciar
     * sesion.
     *
     * <h2>Como se sabe que el 401 es el bueno</h2>
     * Por el mensaje. El del filtro es el generico de falta de token; este es
     * el del servicio, que significa que la peticion llego al controlador, se
     * leyo el cuerpo y se compararon credenciales. El codigo es 401 en los dos
     * casos, asi que sin mirar el mensaje la prueba pasaria igual sin el
     * arreglo.
     */
    @Test
    void loginDeAuth_conCabeceraAuthorizationInservible_sigueAtendiendo() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer esto-no-es-un-token!!!")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"no.existe.seg.h01\",\"clave\":\"no-importa\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Usuario o clave incorrectos"));
    }

    /**
     * El mismo caso con un token bien formado pero sin firma valida, que es la
     * otra forma en que llega una cabecera inservible: un token caducado y
     * manipulado, o de otro despliegue.
     */
    @Test
    void loginDeAuth_conTokenConFirmaAlterada_sigueAtendiendo() throws Exception {
        String token = conLaFirmaAlterada(tokenCon(List.of("ADMIN"), List.of()));

        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"no.existe.seg.h01\",\"clave\":\"no-importa\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Usuario o clave incorrectos"));
    }

    /**
     * Y la recuperacion de clave, que es la ruta que necesita justamente quien
     * no puede autenticarse. Responde 202 aunque el correo no exista --no
     * confirma si hay cuenta-- asi que lo que se comprueba es que ya no es un
     * 401 del filtro.
     */
    @Test
    void recuperacionDeClave_conCabeceraAuthorizationInservible_sigueAtendiendo() throws Exception {
        mockMvc.perform(post("/api/usuarios/recuperar-clave")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer esto-no-es-un-token!!!")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"no.existe.seg.h01@correo.local\"}"))
                .andExpect(status().isAccepted());
    }

    /**
     * El contrapunto: en los GET publicos del catalogo el token NO se ignora,
     * porque ahi si cambia la respuesta (VisibilidadDeEspecie deja ver las
     * fichas no publicadas a quien trae ESPECIE_CONSULTAR). Una cabecera
     * inservible en esas rutas tiene que seguir dando 401 y no colarse como
     * anonimo.
     */
    @Test
    void catalogoPublico_conCabeceraAuthorizationInservible_sigueDando401() throws Exception {
        mockMvc.perform(get(RUTA_PUBLICA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer esto-no-es-un-token!!!"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginViejoDeUsuarios_sigueSiendoPublico() throws Exception {
        mockMvc.perform(post("/api/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"no.existe\",\"clave\":\"no-importa\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Usuario o clave incorrectos"));
    }

    @Test
    void escrituraEnElCatalogo_sinToken_devuelve401() throws Exception {
        mockMvc.perform(post(RUTA_ESCRITURA_CATALOGO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Cambio de clave: guard de propiedad (IDOR) ----------

    /**
     * Cuerpo valido para {@code UsuarioCambioClaveRequest}: la clave nueva
     * cumple el minimo de 8, la mayuscula, el digito y el caracter especial.
     * Se usa uno valido a proposito, para que si una prueba falla no sea por
     * Bean Validation sino por lo que se esta midiendo.
     */
    private static final String CUERPO_CAMBIO_CLAVE =
            "{\"claveActual\":\"LaQueSea1#\",\"claveNueva\":\"Intruso99#\"}";

    /**
     * Token cuyo {@code sub} es el id indicado.
     * <p>
     * Lleva el rol ADMIN a proposito: el guard de
     * {@code PUT /api/usuarios/{id}/clave} es de propiedad, no de rol, asi que
     * ni siquiera un administrador puede cambiarle la clave a otro por esta
     * via. Ese reseteo, si el equipo lo quiere, es otro endpoint.
     */
    private String tokenDeUsuario(Long idUsuario) {
        UsuarioResponse usuario = UsuarioResponse.builder()
                .id(idUsuario)
                .nombres("Prueba")
                .apellidos("Automatizada")
                .correo("usuario" + idUsuario + "@local")
                .nombreUsuario("usuario" + idUsuario)
                .activo(true)
                .build();

        return jwtService.emitir(usuario, List.of("ADMIN"), List.of()).token();
    }

    @Test
    void cambioDeClave_conElIdDeOtroUsuario_devuelve403ConElFormatoDeErrorDelProyecto() throws Exception {
        mockMvc.perform(put("/api/usuarios/1/clave")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeUsuario(999L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_CAMBIO_CLAVE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.estado").value(403))
                .andExpect(jsonPath("$.error").value("Prohibido"))
                .andExpect(jsonPath("$.ruta").value("/api/usuarios/1/clave"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.mensaje").exists())
                // Nada de trazas ni de detalle interno en el cuerpo.
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    /**
     * El guard corre ANTES de validar la clave actual, y esta prueba es la que
     * lo demuestra: el id 99999 no existe en ninguna base. Si el orden fuera
     * el contrario, la logica de negocio habria corrido primero y la respuesta
     * seria 404, con lo que cualquiera con un token podria averiguar que ids
     * existen. Al dar 403 queda claro que la peticion se corto antes de tocar
     * la base.
     */
    @Test
    void cambioDeClave_sobreUnIdAjenoQueNiExiste_devuelve403YNo404() throws Exception {
        mockMvc.perform(put("/api/usuarios/99999/clave")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeUsuario(999L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_CAMBIO_CLAVE))
                .andExpect(status().isForbidden());
    }

    /**
     * Las dos tentativas contra una cuenta ajena --con la clave actual
     * correcta y con una cualquiera-- tienen que ser indistinguibles desde
     * fuera. Si se diferenciaran, el endpoint serviria para comprobar claves
     * de otras cuentas.
     */
    @Test
    void cambioDeClave_sobreCuentaAjena_noDistingueSiLaClaveActualEraCorrecta() throws Exception {
        String token = tokenDeUsuario(999L);

        mockMvc.perform(put("/api/usuarios/1/clave")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claveActual\":\"UnaCualquiera1#\",\"claveNueva\":\"Intruso99#\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("No tiene los permisos necesarios para realizar esta operación"));

        mockMvc.perform(put("/api/usuarios/1/clave")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claveActual\":\"OtraDistinta2$\",\"claveNueva\":\"Intruso99#\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("No tiene los permisos necesarios para realizar esta operación"));
    }

    @Test
    void cambioDeClave_sinToken_sigueDando401YNo403() throws Exception {
        mockMvc.perform(put("/api/usuarios/1/clave")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_CAMBIO_CLAVE))
                .andExpect(status().isUnauthorized());
    }

    /**
     * El autoservicio no se rompio: con su propio id la peticion atraviesa el
     * guard y llega a la logica de siempre, que rechaza la clave actual
     * equivocada con el 401 de {@code CredencialesInvalidasException}.
     * <p>
     * Siembra su usuario en vez de tomar el primero de la base --que es lo que
     * hacia, saltandose con {@code assumeTrue} si no habia ninguno-- y le pone
     * una clave que NO es la de {@link #CUERPO_CAMBIO_CLAVE}, para que el 401
     * que se afirma sea el de la clave actual equivocada y no una casualidad.
     */
    @Test
    @Transactional
    void cambioDeClave_sobreSuPropioId_atraviesaElGuardYLlegaALaValidacionDeClave() throws Exception {
        Long idPropio = sembrarUsuario("LaDeVerdadDeEste1#").getIdUsuario();

        mockMvc.perform(put("/api/usuarios/" + idPropio + "/clave")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeUsuario(idPropio)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_CAMBIO_CLAVE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("La clave actual no es correcta"));
    }

    // ---------- Roles dados de baja ----------

    /**
     * Un rol desactivado no debe viajar en el token: desactivarlo es la forma
     * de revocar lo que concedia, y meterlo en el token seria devolverselo al
     * usuario hasta que caduque.
     * <p>
     * El caso lo siembra la prueba --un usuario con dos roles asignados, uno
     * vigente y uno dado de baja-- en vez de buscar en la base a alguien que
     * lo tuviera y saltarse si no habia nadie. Asi corre siempre y, ademas,
     * las dos listas son conocidas: se puede afirmar exactamente cual rol es
     * el vigente y cual el revocado, cosa que con un usuario cualquiera de la
     * base no se podia.
     */
    @Test
    @Transactional
    void rolesDesactivados_noViajanEnElToken() {
        Usuario sembrado = sembrarUsuario("NadieEntraConEste1#");
        Rol vigente = sembrarRol(true);
        Rol dadoDeBaja = sembrarRol(false);
        asignar(sembrado, vigente);
        asignar(sembrado, dadoDeBaja);

        Long idUsuario = sembrado.getIdUsuario();
        List<String> inactivos = rolesInactivosDe(idUsuario);
        List<String> vigentes = usuarioRolService.obtenerRolesDeUsuario(idUsuario, true)
                .stream()
                .map(RolResponse::getNombre)
                .toList();

        // La siembra quedo como se esperaba: el historico trae el rol revocado
        // y lo vigente trae solo el otro. Sin esto, un fallo del filtro de
        // soloActivos pasaria por una prueba verde con las dos listas vacias.
        assertEquals(List.of(dadoDeBaja.getNombre()), inactivos,
                "el historico del usuario sembrado debe traer el rol dado de baja");
        assertEquals(List.of(vigente.getNombre()), vigentes,
                "lo vigente debe ser solo el rol activo");

        UsuarioResponse usuario = usuarioDePrueba();
        String token = jwtService.emitir(usuario, vigentes, List.of()).token();
        List<String> rolesDelToken = jwtDecoder.decode(token).getClaimAsStringList("roles");

        assertEquals(vigentes, rolesDelToken);
        for (String inactivo : inactivos) {
            assertFalse(rolesDelToken.contains(inactivo),
                    "el rol dado de baja " + inactivo + " no debe estar en el token");
        }
    }

    private List<String> rolesInactivosDe(Long idUsuario) {
        return usuarioRolService.obtenerRolesDeUsuario(idUsuario, false).stream()
                .filter(rol -> !Boolean.TRUE.equals(rol.getActivo()))
                .map(RolResponse::getNombre)
                .toList();
    }

    private String tokenExpirado() {
        // Dos horas en el pasado, muy por fuera del margen de reloj de 60
        // segundos que Nimbus tolera por omision.
        Instant hace2Horas = Instant.now().minus(Duration.ofHours(2));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("999")
                .issuedAt(hace2Horas)
                .expiresAt(hace2Horas.plusSeconds(60))
                .claim("nombreUsuario", "prueba.automatizada")
                .claim("roles", List.of("ADMIN"))
                .claim("permisos", List.of())
                .build();

        return jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
