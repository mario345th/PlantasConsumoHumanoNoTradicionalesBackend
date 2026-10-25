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
import java.util.Optional;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
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
// En Spring Boot 4 las autoconfiguraciones de prueba se repartieron por
// modulo: @AutoConfigureMockMvc ya no esta en
// org.springframework.boot.test.autoconfigure.web.servlet sino aqui, en el
// modulo spring-boot-webmvc-test.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import sv.edu.ues.fmp.flora.dto.response.RolResponse;
import sv.edu.ues.fmp.flora.dto.response.UsuarioResponse;
import sv.edu.ues.fmp.flora.entity.Usuario;
import sv.edu.ues.fmp.flora.repository.UsuarioRepository;
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
 * sin pasar por {@code POST /api/auth/login}. No es un atajo: ninguna
 * migracion siembra un usuario con clave conocida --{@code V3} quedo fuera
 * por el {@code baseline-version=3} de Flyway-- asi que una prueba que
 * dependiera de una clave en texto plano solo correria en la maquina de quien
 * la escribio. Lo que si depende de la clave ("clave correcta devuelve 200")
 * se verifico a mano contra la aplicacion corriendo.
 * <p>
 * Para la autenticacion da lo mismo: el filtro de Spring Security solo mira
 * la firma y la vigencia del token, no consulta la base para saber si el
 * usuario existe. Un token bien firmado entra aunque su {@code sub} no
 * corresponda a ninguna fila.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AutenticacionJwtTest {

    /** Ruta protegida cualquiera, sirve para comprobar que exige token. */
    private static final String RUTA_PROTEGIDA = "/api/usuarios";

    /** Ruta del catalogo que la matriz de arranque deja abierta en GET. */
    private static final String RUTA_PUBLICA = "/api/especies";

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
    private UsuarioRepository usuarioRepository;

    @Autowired
    private UsuarioRolService usuarioRolService;

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

    @Test
    void rutaProtegida_conTokenValido_devuelve200() throws Exception {
        String token = tokenCon(List.of("ADMIN"), List.of());

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
        mockMvc.perform(get(RUTA_PUBLICA).param("pagina", "0").param("tamanio", "1"))
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
        mockMvc.perform(post(RUTA_PUBLICA)
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
     * Toma cualquier usuario de la base en vez de uno fijo: ninguna migracion
     * siembra usuarios (ver el javadoc de la clase), asi que fijar un id
     * concreto haria la prueba dependiente de la maquina.
     */
    @Test
    void cambioDeClave_sobreSuPropioId_atraviesaElGuardYLlegaALaValidacionDeClave() throws Exception {
        Optional<Usuario> alguno = usuarioRepository.findAll().stream().findFirst();
        Assumptions.assumeTrue(alguno.isPresent(), "no hay usuarios en la base");

        Long idPropio = alguno.get().getIdUsuario();

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
     * Depende de que exista en la base un usuario con al menos un rol dado de
     * baja. Ninguna migracion lo siembra, asi que la prueba se salta cuando no
     * lo encuentra en vez de fallar en la maquina de quien no lo tenga.
     */
    @Test
    void rolesDesactivados_noViajanEnElToken() {
        Optional<Usuario> candidato = usuarioRepository.findAll().stream()
                .filter(usuario -> !rolesInactivosDe(usuario.getIdUsuario()).isEmpty())
                .findFirst();

        Assumptions.assumeTrue(candidato.isPresent(),
                "no hay en la base ningun usuario con un rol dado de baja");

        Long idUsuario = candidato.get().getIdUsuario();
        List<String> inactivos = rolesInactivosDe(idUsuario);
        List<String> vigentes = usuarioRolService.obtenerRolesDeUsuario(idUsuario, true)
                .stream()
                .map(RolResponse::getNombre)
                .toList();

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
