package sv.edu.ues.fmp.flora.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

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
 *     <td>{@code /api/auth/login}</td>
 *     <td>publico</td>
 *     <td>Es donde se consigue el token; exigir token aqui seria circular. Es
 *         la ruta exacta y no {@code /api/auth/**}: ver {@link #PUBLICAS}.</td>
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
 *     <td>{@code GET} de las rutas en {@link #LISTADOS_GLOBALES}</td>
 *     <td><strong>ESPECIE_CONSULTAR (cambia)</strong></td>
 *     <td>Listados que recorren el contenido de todas las especies sin mirar
 *         el estado de ninguna. Eran publicos y mostraban tambien los
 *         renglones de los borradores.</td>
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
 *
 * <h2>Lo publico es publico aunque llegue una cabecera rota</h2>
 * El filtro del token corre antes que esta matriz, asi que en las rutas
 * publicas hay que decirle explicitamente que no mire la cabecera
 * {@code Authorization}: ver {@link #resolvedorDeToken()}.
 */
@Configuration
@EnableMethodSecurity
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
     * <p>
     * Los comodines de esta lista ya no son completos: {@link
     * #LISTADOS_GLOBALES} recorta de ellos cinco {@code GET} concretos, y por
     * eso esa lista se registra antes en {@code authorizeHttpRequests}. Al
     * agregar una ruta aqui conviene revisar si su listado sin filtros cae en
     * el mismo caso.
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
     * Listados globales de contenido de especie: sus {@code GET} pasan a
     * exigir {@code ESPECIE_CONSULTAR}, igual que {@code GET /api/especies}.
     *
     * <h2>Que fuga cierran</h2>
     * Los cinco recorren el contenido de <strong>todas</strong> las especies
     * --un {@code findAll()}, o un filtro por nutriente-- y arman la respuesta
     * sin mirar el estado de la ficha de la que cuelga cada renglon. Los
     * cambios anteriores cerraron las consultas que entran por el id de una
     * especie o por el de un hijo suyo; estas no reciben ningun id de ficha, y
     * por eso se escaparon: sin token devolvian tambien las preparaciones, los
     * beneficios, los aportes y las epocas de las fichas BORRADOR y
     * EN_REVISION.
     *
     * <h2>Por que la matriz y no la logica</h2>
     * Aqui no hay un 404 que cuidar, al reves que en las rutas planas. Un
     * listado no se pide por el id de nada, asi que cerrarlo no confirma que
     * exista ningun registro concreto y el 401 del filtro no delata nada. Son
     * listados administrativos: el catalogo publico no los llama, pinta el
     * contenido de una especie con las rutas por padre, que siguen abiertas.
     * <p>
     * Van <strong>antes</strong> que {@link #CATALOGO_PUBLICO} en
     * {@code authorizeHttpRequests}: gana la primera regla que coincide y los
     * comodines de esa lista cubren estas cinco rutas. Quien pide el permiso es
     * el {@code @PreAuthorize} de cada metodo; esta entrada es la que hace que
     * al anonimo lo corte el filtro con un 401 en vez de dejarlo llegar.
     *
     * <h2>Lo que no entra aqui</h2>
     * Los listados hermanos que si reciben el id del padre
     * --{@code /api/preparaciones-consumo/parte/{id}} y
     * {@code /api/aportes-nutricionales/especie-parte/{id}}-- se quedan
     * publicos: ya preguntan por el estado de la ficha dentro del servicio.
     */
    private static final String[] LISTADOS_GLOBALES = {
            "/api/preparaciones-consumo",
            "/api/parte-beneficios",
            "/api/parte-beneficios/activas",
            "/api/aportes-nutricionales/nutriente/*",
            "/api/epocas-cosecha"
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
     * <h2>Los sub-recursos: alcanzables aqui, filtrados en la logica</h2>
     * Los sub-recursos de una especie (sus fuentes, habitats, imagenes,
     * nombres comunes, partes comestibles y videos) siguen aqui en
     * {@code GET}, porque son los que pinta la ficha publica y cerrarlos en el
     * filtro romperia el catalogo. Que se vea su contenido <strong>ya no lo
     * decide esta matriz</strong>: cada uno de esos seis {@code GET} pregunta a
     * {@code VisibilidadDeEspecie}, que deja pasar si la especie esta
     * PUBLICADA, o si quien llama trae {@code ESPECIE_CONSULTAR}, y responde
     * 404 en el resto.
     * <p>
     * La decision tiene que estar en la logica y no aqui por el 404: el filtro
     * solo sabe contestar 401 o 403, y un 403 sobre un borrador confirmaria que
     * ese id existe. El 404 lo deja indistinguible de un id inventado.
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

    /**
     * Los tres {@code POST} del modulo de usuarios que no pueden exigir token.
     * <p>
     * Estan en una constante, y no escritos en el {@code authorizeHttpRequests},
     * porque {@link #rutasSinToken()} necesita exactamente la misma lista: si
     * una ruta se agregara en un sitio y no en el otro, quedaria abierta pero
     * una cabecera {@code Authorization} mal formada la seguiria tumbando con
     * un 401.
     */
    private static final String[] USUARIOS_PUBLICOS_POST = {
            "/api/usuarios/login",
            "/api/usuarios/recuperar-clave",
            "/api/usuarios/restablecer-clave"
    };

    /**
     * Rutas abiertas sin importar el verbo.
     *
     * <h2>Por que {@code /api/auth/login} y no {@code /api/auth/**} (H5)</h2>
     * El comodin abria <strong>todo</strong> lo que algun dia cuelgue de
     * {@code /api/auth}, no solo lo que hay hoy. Bajo ese prefijo existe un
     * unico endpoint publico --{@code POST /api/auth/login}-- y los que el
     * propio {@code AuthController} ya tiene anotados como pendientes
     * ({@code GET /api/auth/me}, los refresh tokens) no tienen por que ser
     * publicos: {@code me} devuelve la identidad de quien llama y un refresh
     * emite credenciales. Con el comodin habrian nacido {@code permitAll} por
     * olvido, sin que nadie tuviera que decidirlo.
     * <p>
     * Acotarlo a la ruta exacta no cambia el login: sigue siendo la misma
     * ruta, abierta igual y con la cabecera {@code Authorization} ignorada por
     * {@link #resolvedorDeToken()}, que se arma de esta misma constante. Lo que
     * cambia es que un endpoint nuevo bajo {@code /api/auth} cae en el
     * {@code anyRequest().authenticated()} y, si de verdad tiene que ser
     * publico, se agrega aqui a proposito.
     * <p>
     * Queda publica para cualquier verbo, no solo para {@code POST}: el metodo
     * lo decide el {@code @PostMapping} del controlador, que responde 405 a los
     * demas. Un 405 sin mirar el token es lo correcto en una ruta que no pide
     * identidad.
     */
    private static final String[] PUBLICAS = {
            "/api/auth/login",
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

    /**
     * Constructor escrito a mano, en vez del {@code @RequiredArgsConstructor}
     * que usa el resto del proyecto, por el {@code @Qualifier} de abajo.
     *
     * <h2>Por que no basta anotar el campo</h2>
     * Lombok no copia {@code @Qualifier} del campo al parametro del
     * constructor: habria que declararlo en {@code lombok.copyableAnnotations}
     * dentro de un {@code lombok.config}, que este proyecto no tiene. Y con
     * inyeccion por constructor Spring lee las anotaciones del
     * <strong>parametro</strong>, no las del campo, asi que un
     * {@code @Qualifier} sobre el campo final no lo mira nadie: se compila, no
     * avisa, y no hace nada.
     *
     * <h2>Que resuelve el qualifier</h2>
     * En el contexto hay dos beans de tipo {@link CorsConfigurationSource}: el
     * de {@link CorsConfig} y {@code mvcHandlerMappingIntrospector}, porque
     * {@code HandlerMappingIntrospector} tambien implementa esa interfaz. Sin
     * el qualifier la ambiguedad la deshacia la coincidencia entre el nombre
     * del parametro y el del bean, que es exactamente lo que no debe sostener
     * una decision de seguridad: al renombrar cualquiera de los dos, el CORS
     * pasaria a salir del introspector de MVC sin un solo error. Con el
     * qualifier, el bean se pide por su nombre y si no esta, el contexto no
     * arranca.
     */
    public SecurityConfig(
            JwtDecoder jwtDecoder,
            @Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource,
            AutenticacionEntryPoint autenticacionEntryPoint,
            AccesoDenegadoHandler accesoDenegadoHandler) {

        this.jwtDecoder = jwtDecoder;
        this.corsConfigurationSource = corsConfigurationSource;
        this.autenticacionEntryPoint = autenticacionEntryPoint;
        this.accesoDenegadoHandler = accesoDenegadoHandler;
    }

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
                        .requestMatchers(HttpMethod.POST, USUARIOS_PUBLICOS_POST).permitAll()
                        // Recorte de los comodines de CATALOGO_PUBLICO: los
                        // cinco listados globales quedan fuera de lo publico.
                        // Tiene que ir antes del permitAll de abajo, porque
                        // gana la primera regla que coincide.
                        .requestMatchers(HttpMethod.GET, LISTADOS_GLOBALES).authenticated()
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
                        // SEG-H01: en las rutas publicas la cabecera
                        // Authorization no se mira. Ver resolvedorDeToken().
                        .bearerTokenResolver(resolvedorDeToken())
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
     * De donde saca el filtro el token, con una excepcion: en las rutas que
     * nunca necesitan identidad, de ningun sitio.
     *
     * <h2>El fallo que cierra (SEG-H01)</h2>
     * {@code BearerTokenAuthenticationFilter} corre ANTES de que se evalue la
     * matriz de rutas, asi que no sabe --ni puede saber-- que la ruta era
     * publica: si la peticion trae una cabecera {@code Authorization}, intenta
     * autenticarla, y si no puede corta con un 401. El resultado era que un
     * {@code Bearer} basura tumbaba {@code POST /api/auth/login},
     * {@code recuperar-clave} y {@code restablecer-clave} con un 401 antes de
     * que el controlador llegara a atenderlas. Y basta con muy poco: el
     * resolvedor por omision exige que lo que sigue a "Bearer " encaje con
     * {@code [a-zA-Z0-9-._~+/]+=*}, de modo que una cabecera con un caracter
     * raro ya se rechaza como token mal formado sin mirar ninguna firma.
     * <p>
     * Es un escenario real, no teorico: un cliente que guarda el token y lo
     * manda en todas las peticiones --lo normal en un frontend-- se queda sin
     * poder renovar la sesion ni pedir el restablecimiento de clave justo
     * cuando su token se corrompio o caduco, que es cuando mas lo necesita.
     * <p>
     * Devolver {@code null} hace que el filtro no intente nada y deje pasar la
     * peticion al {@code permitAll} que le toca. El token, valido o no, se
     * ignora.
     *
     * <h2>Donde NO se ignora, y por que</h2>
     * Solo en las rutas abiertas sin importar el verbo ({@link #PUBLICAS}) y en
     * los tres POST de {@link #USUARIOS_PUBLICOS_POST}. Los {@code GET}
     * publicos del catalogo quedan fuera a proposito: ahi el token
     * <strong>si</strong> cambia la respuesta --{@code VisibilidadDeEspecie}
     * deja ver el contenido de una ficha no publicada a quien traiga
     * {@code ESPECIE_CONSULTAR}--, asi que descartarlo silenciosamente
     * convertiria a un usuario autorizado en un anonimo y le respondería 404
     * sobre fichas que si puede ver. En esas rutas un token invalido tiene que
     * seguir dando 401, que es lo correcto: lo que fallo es la credencial que
     * el cliente eligio mandar.
     */
    @Bean
    public BearerTokenResolver resolvedorDeToken() {
        BearerTokenResolver porOmision = new DefaultBearerTokenResolver();
        RequestMatcher sinToken = rutasSinToken();

        return peticion -> sinToken.matches(peticion) ? null : porOmision.resolve(peticion);
    }

    /**
     * Las rutas de {@link #resolvedorDeToken()}, armadas a partir de las mismas
     * constantes que usa la matriz de autorizacion para no repetir los patrones
     * en dos sitios.
     * <p>
     * {@code AntPathRequestMatcher} ya no existe en Spring Security 7; el
     * equivalente es {@link PathPatternRequestMatcher}, que entiende los mismos
     * comodines ({@code *} un segmento, {@code **} el resto de la ruta).
     */
    private static RequestMatcher rutasSinToken() {
        List<RequestMatcher> patrones = new ArrayList<>();

        for (String publica : PUBLICAS) {
            patrones.add(PathPatternRequestMatcher.withDefaults().matcher(publica));
        }
        for (String publica : USUARIOS_PUBLICOS_POST) {
            patrones.add(PathPatternRequestMatcher.withDefaults()
                    .matcher(HttpMethod.POST, publica));
        }

        return new OrRequestMatcher(patrones);
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
