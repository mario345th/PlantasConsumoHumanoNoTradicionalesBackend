package sv.edu.ues.fmp.flora.config;

import java.util.ArrayList;
import java.util.Collection;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.exception.AccesoDenegadoHandler;
import sv.edu.ues.fmp.flora.exception.AutenticacionEntryPoint;

/**
 * Cadena de seguridad de la API.
 *
 * <h2>Aviso de alcance</h2>
 * Esta clase es lo primero que toca <strong>todos</strong> los endpoints del
 * proyecto, no solo los del modulo de usuarios. Antes de ella no habia Spring
 * Security y cualquiera podia llamar a cualquier ruta sin identificarse.
 * <p>
 * <strong>La matriz publico/protegido definitiva la aprueba el equipo; esta es
 * la de arranque para no romper a QA.</strong> El criterio con el que se
 * escribio: todo lo que hoy funciona sin login y es consulta publica del
 * catalogo sigue abierto; lo que escribe en el catalogo y todo el modulo de
 * seguridad (usuarios, roles, permisos y sus cruces) pasa a exigir token. Para
 * ajustarla solo hay que mover una ruta entre las constantes de abajo y el
 * bloque {@code authorizeHttpRequests}.
 *
 * <h2>Matriz de arranque</h2>
 * <table border="1">
 *   <caption>Quien puede llamar a que</caption>
 *   <tr><th>Rutas</th><th>Acceso</th><th>Por que</th></tr>
 *   <tr>
 *     <td>{@code /api/auth/**}</td>
 *     <td>publico</td>
 *     <td>Es donde se consigue el token; exigir token aqui seria circular.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code POST /api/usuarios/login}</td>
 *     <td>publico</td>
 *     <td>El login viejo, el que no emite token. Se deja abierto para que el
 *         frontend no se rompa mientras migra a {@code /api/auth/login}.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code POST /api/usuarios/recuperar-clave},
 *         {@code POST /api/usuarios/restablecer-clave}</td>
 *     <td>publico</td>
 *     <td>Publicos por naturaleza: quien olvido la clave no puede traer un
 *         token. Su barrera es el token de un solo uso que llega al correo.</td>
 *   </tr>
 *   <tr>
 *     <td>Swagger ({@code /swagger-ui/**}, {@code /swagger-ui.html},
 *         {@code /v3/api-docs/**})</td>
 *     <td>publico</td>
 *     <td>QA y el frontend lo usan a diario. En produccion no deberia ni
 *         existir: ya hay una nota en application.properties sobre apagarlo
 *         con un perfil.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code GET} de las rutas en {@link #CATALOGO_PUBLICO}</td>
 *     <td>publico</td>
 *     <td>Es el catalogo botanico, el producto de cara al publico. Hoy se lee
 *         sin login y asi se queda.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code GET} de las rutas en {@link #ESPECIE_PUBLICA}</td>
 *     <td>publico</td>
 *     <td>Las consultas de especie que solo devuelven fichas PUBLICADA, mas
 *         los sub-recursos que pinta la ficha publica.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code GET /api/especies} y {@code GET /api/especies/{id}}</td>
 *     <td><strong>ESPECIE_CONSULTAR (cambia)</strong></td>
 *     <td>Devuelven fichas en cualquier estado. Eran publicas y filtraban
 *         borradores a cualquier anonimo.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code POST}, {@code PUT}, {@code PATCH} y {@code DELETE} de esas
 *         mismas rutas del catalogo</td>
 *     <td><strong>autenticado (cambia)</strong></td>
 *     <td>Escribir en el catalogo deja de ser anonimo.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code /api/usuarios/**} (lo demas), {@code /api/roles/**},
 *         {@code /api/permisos/**}, {@code /api/usuario-roles/**},
 *         {@code /api/rol-permisos/**}</td>
 *     <td><strong>autenticado (cambia)</strong></td>
 *     <td>Es el modulo de seguridad. Listar usuarios o roles sin
 *         identificarse no deberia haber sido posible nunca.</td>
 *   </tr>
 *   <tr>
 *     <td>cualquier otra</td>
 *     <td>autenticado</td>
 *     <td>Por omision se cierra, para que una ruta nueva no quede abierta por
 *         olvido.</td>
 *   </tr>
 * </table>
 *
 * <h2>Donde se decide el permiso concreto</h2>
 * Esta matriz solo distingue anonimo de autenticado. Que permiso hace falta
 * para cada operacion se decide con {@code @PreAuthorize} en los
 * controladores, habilitado por {@link EnableMethodSecurity}. Los dos niveles
 * se complementan y el orden importa: quien llega sin token lo corta el filtro
 * con un 401 y nunca llega al metodo; quien llega con token pero sin el
 * permiso lo corta method-security con un 403.
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * Rutas del catalogo botanico cuyos {@code GET} quedan publicos.
     * <p>
     * {@code /api/preparaciones-consumo/**} cubre tambien sus imagenes y
     * videos.
     * <p>
     * Las rutas de especie <strong>no</strong> estan aqui: tienen su propia
     * lista en {@link #ESPECIE_PUBLICA}, porque no todas sus consultas se
     * pueden abrir igual.
     */
    private static final String[] CATALOGO_PUBLICO = {
            "/api/aportes-nutricionales/**",
            "/api/beneficios/**",
            "/api/departamentos/**",
            "/api/epocas-cosecha/**",
            "/api/fuentes/**",
            "/api/habitats/**",
            "/api/imagenes-especie/**",
            "/api/imagenes-preparacion/**",
            "/api/municipios/**",
            "/api/nombres-comunes/**",
            "/api/nutrientes/**",
            "/api/parte-beneficios/**",
            "/api/partes-comestibles/**",
            "/api/partes-planta/**",
            "/api/preparaciones-consumo/**",
            "/api/ubicaciones/**",
            "/api/videos-especie/**",
            "/api/videos-preparacion/**"
    };

    /**
     * Rutas de especie cuyos {@code GET} quedan abiertos al anonimo.
     *
     * <h2>Que cambio y por que</h2>
     * Antes aqui estaba {@code /api/especies/**} entero, dentro de
     * {@link #CATALOGO_PUBLICO}. Eso dejaba publicos tambien
     * {@code GET /api/especies} --que por dentro es un {@code findAll()}-- y
     * {@code GET /api/especies/{id}}, que devuelve la ficha en cualquier
     * estado. Cualquiera sin token podia listar los BORRADOR y EN_REVISION, o
     * pedirlos con {@code ?estado=BORRADOR}. Esos dos endpoints ahora exigen
     * {@code ESPECIE_CONSULTAR} y no figuran en ninguna lista de permitidos.
     * <p>
     * Queda abierto solo lo que por construccion no puede devolver otra cosa
     * que fichas PUBLICADA y activas: el listado del catalogo y el detalle
     * {@code /publicadas/{id}}, que resuelve a 404 lo que no este publicado.
     *
     * <h2>Lo que sigue abierto y no deberia estarlo del todo</h2>
     * Los sub-recursos de una especie (sus fuentes, habitats, imagenes,
     * nombres comunes, partes comestibles y videos) siguen publicos en
     * {@code GET}: son los que pinta la ficha publica y cerrarlos romperia el
     * catalogo. Pero <strong>ninguno filtra por el estado de la especie
     * padre</strong>, asi que quien conozca el id de un borrador puede ver por
     * esta via su multimedia y sus nombres comunes. Cerrarlo pide que cada uno
     * de esos seis servicios compruebe el estado del padre; no entra en este
     * cambio y queda anotado para el equipo.
     */
    private static final String[] ESPECIE_PUBLICA = {
            "/api/especies/publicadas",
            "/api/especies/publicadas/*",
            // Las dos formas de cada sub-recurso: con y sin segmentos
            // adicionales, para no depender de si el comodin de cola tambien
            // casa la ruta sin barra final.
            "/api/especies/*/fuentes",
            "/api/especies/*/fuentes/**",
            "/api/especies/*/habitats",
            "/api/especies/*/habitats/**",
            "/api/especies/*/imagenes",
            "/api/especies/*/imagenes/**",
            "/api/especies/*/nombres-comunes",
            "/api/especies/*/nombres-comunes/**",
            "/api/especies/*/partes-comestibles",
            "/api/especies/*/partes-comestibles/**",
            "/api/especies/*/videos",
            "/api/especies/*/videos/**"
    };

    /** Rutas abiertas sin importar el verbo. */
    private static final String[] PUBLICAS = {
            "/api/auth/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            // Spring reenvia aqui los errores que no atendio el
            // GlobalExceptionHandler. Sin permitAll ese reenvio interno se
            // convierte en un 403 y el cliente recibe un codigo que no tiene
            // nada que ver con lo que fallo de verdad.
            "/error"
    };

    private final JwtDecoder jwtDecoder;
    private final CorsConfigurationSource corsConfigurationSource;
    private final AutenticacionEntryPoint autenticacionEntryPoint;
    private final AccesoDenegadoHandler accesoDenegadoHandler;

    @Bean
    public SecurityFilterChain filtroDeSeguridad(HttpSecurity http) throws Exception {
        http
                // Sin CSRF: no hay sesion ni cookie de sesion que proteger, la
                // credencial viaja en la cabecera Authorization y el navegador
                // no la adjunta por su cuenta.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(sesion -> sesion
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Esta API no tiene formulario de login ni autenticacion
                // basica. Se desactivan explicitamente para que nadie los de
                // por disponibles.
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())

                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers(PUBLICAS).permitAll()
                        // Los tres del modulo de usuarios que no pueden exigir
                        // token. Van antes del cierre general de
                        // /api/usuarios, porque gana la primera regla que
                        // coincide.
                        .requestMatchers(HttpMethod.POST, "/api/usuarios/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/usuarios/recuperar-clave").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/usuarios/restablecer-clave").permitAll()
                        // Consulta del catalogo: abierta. Los verbos de
                        // escritura de esas mismas rutas caen en el
                        // anyRequest() de abajo.
                        .requestMatchers(HttpMethod.GET, CATALOGO_PUBLICO).permitAll()
                        // Especie aparte: solo sus consultas publicas. GET
                        // /api/especies y GET /api/especies/{id} no estan en
                        // esta lista a proposito, caen en el anyRequest() y
                        // ademas exigen ESPECIE_CONSULTAR por @PreAuthorize.
                        .requestMatchers(HttpMethod.GET, ESPECIE_PUBLICA).permitAll()
                        .anyRequest().authenticated())

                .oauth2ResourceServer(servidor -> servidor
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(convertidorDeAutoridades()))
                        // El servidor de recursos trae sus propios manejadores
                        // para el token ausente o invalido; se reemplazan por
                        // los del proyecto o el 401 saldria con cuerpo vacio.
                        .authenticationEntryPoint(autenticacionEntryPoint)
                        .accessDeniedHandler(accesoDenegadoHandler))

                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(autenticacionEntryPoint)
                        .accessDeniedHandler(accesoDenegadoHandler));

        return http.build();
    }

    /**
     * Traduce los claims del token a las autoridades que entienden
     * {@code hasRole} y {@code hasAuthority}.
     * <p>
     * El claim {@code roles} lleva nombres pelados (ADMIN), y
     * {@code hasRole} busca la autoridad con el prefijo {@code ROLE_}: de ahi
     * que se lo agregue aqui. El claim {@code permisos} va sin prefijo, porque
     * {@code hasAuthority} compara el codigo del permiso tal cual.
     * <p>
     * Un token sin alguno de los dos claims no es un error: el convertidor
     * devuelve una lista vacia y el usuario queda autenticado pero sin esa
     * clase de autoridades.
     * <p>
     * Es un bean y no un metodo privado para que se pueda verificar por
     * separado en las pruebas, sin levantar toda la cadena de filtros, y para
     * que el equipo pueda reemplazarlo si cambia el nombre de los claims.
     */
    @Bean
    public JwtAuthenticationConverter convertidorDeAutoridades() {
        JwtGrantedAuthoritiesConverter desdeRoles = new JwtGrantedAuthoritiesConverter();
        desdeRoles.setAuthoritiesClaimName("roles");
        desdeRoles.setAuthorityPrefix("ROLE_");

        JwtGrantedAuthoritiesConverter desdePermisos = new JwtGrantedAuthoritiesConverter();
        desdePermisos.setAuthoritiesClaimName("permisos");
        desdePermisos.setAuthorityPrefix("");

        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter((Jwt token) -> {
            Collection<GrantedAuthority> autoridades = new ArrayList<>();
            agregar(autoridades, desdeRoles.convert(token));
            agregar(autoridades, desdePermisos.convert(token));
            return autoridades;
        });

        return convertidor;
    }

    private static void agregar(
            Collection<GrantedAuthority> destino,
            Collection<GrantedAuthority> origen) {

        if (origen != null) {
            destino.addAll(origen);
        }
    }
}
