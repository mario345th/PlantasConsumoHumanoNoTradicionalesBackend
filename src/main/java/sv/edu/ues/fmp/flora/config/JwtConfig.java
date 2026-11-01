package sv.edu.ues.fmp.flora.config;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * Piezas criptograficas del JWT: la clave simetrica y los dos beans que la
 * usan, uno para firmar ({@link JwtEncoder}, lo usa el login) y otro para
 * verificar ({@link JwtDecoder}, lo usa el filtro de Spring Security en cada
 * peticion con cabecera {@code Authorization}).
 * <p>
 * Se firma con HS256 (HMAC-SHA256), no con un par de claves: la misma clave
 * firma y verifica. Es lo que corresponde cuando quien emite y quien valida
 * son el mismo servicio, como aqui. Si algun dia otro servicio tuviera que
 * validar tokens de este sin poder emitirlos, habria que pasar a RS256.
 * <p>
 * El secreto nunca se escribe en el codigo: viene de {@code app.jwt.secret},
 * que a su vez sale de la variable de entorno {@code JWT_SECRET} y solo cae al
 * marcador de desarrollo cuando esa variable no existe.
 *
 * <h2>Las dos validaciones del arranque</h2>
 * Las dos viven en {@link #validarSecreto(String, Collection)} y revientan el
 * arranque, no la primera peticion:
 * <ol>
 *   <li><strong>Longitud.</strong> HS256 pide 256 bits. Nimbus tambien lo
 *       exige, pero lo descubre al firmar el primer token; comprobarlo aqui
 *       convierte "el login devuelve 500" en "la aplicacion no arranca y dice
 *       por que".</li>
 *   <li><strong>El secreto de desarrollo no vale en produccion.</strong> El
 *       valor por omision de {@code app.jwt.secret} esta commiteado, asi que
 *       cualquiera que lea el repositorio puede firmar tokens validos. Antes,
 *       un despliegue que olvidara {@code JWT_SECRET} arrancaba igual y nadie
 *       se enteraba. Ahora, si hay un perfil de produccion activo y el secreto
 *       resuelto sigue siendo el marcador, no arranca.</li>
 * </ol>
 * En desarrollo --sin perfil, o con {@code dev}-- el marcador sigue
 * funcionando igual que antes: la idea es que nadie tenga que configurar nada
 * para levantar el proyecto en su maquina.
 */
@Configuration
public class JwtConfig {

    /**
     * Minimo que exige HS256: 256 bits, es decir 32 bytes.
     */
    private static final int BYTES_MINIMOS = 32;

    /**
     * El valor por omision de {@code app.jwt.secret} en
     * {@code application.properties}, duplicado aqui para poder reconocerlo.
     * <p>
     * Es duplicacion y hay que saberlo: si alguien cambia el marcador en
     * {@code application.properties} y no toca esta constante, la validacion
     * de produccion deja de reconocerlo y vuelve el agujero en silencio. Por
     * eso {@code JwtConfigSecretoTest} compara las dos cadenas y falla si se
     * separan.
     */
    static final String SECRETO_DE_DESARROLLO =
            "flora-ues-fmp-secreto-de-desarrollo-cambiar-en-produccion-2026";

    /**
     * Nombres de perfil que se consideran produccion.
     * <p>
     * Son varios porque el despliegue todavia no esta decidido y no se sabe
     * cual va a usar el equipo; reconocer los tres sale gratis y evita que la
     * proteccion quede desactivada por haber escrito {@code production} donde
     * el codigo esperaba {@code prod}.
     */
    private static final Set<String> PERFILES_DE_PRODUCCION =
            Set.of("prod", "produccion", "production");

    private final SecretKey claveSimetrica;

    /**
     * Toma el {@link Environment} y no un {@code @Value} con
     * {@code spring.profiles.active} porque {@code getActiveProfiles()} ya
     * resuelve por su cuenta las tres formas de activar un perfil --la
     * propiedad, la variable {@code SPRING_PROFILES_ACTIVE} y el argumento de
     * linea de comandos-- y porque devuelve la lista ya separada.
     */
    public JwtConfig(@Value("${app.jwt.secret}") String secreto, Environment entorno) {
        validarSecreto(secreto, List.of(entorno.getActiveProfiles()));

        this.claveSimetrica = new SecretKeySpec(
                secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /**
     * Las comprobaciones del secreto, aparte del constructor para poder
     * probarlas sin levantar un contexto de Spring.
     *
     * @param secreto         el valor ya resuelto de {@code app.jwt.secret}
     * @param perfilesActivos los perfiles activos; vacio en desarrollo
     * @throws IllegalStateException si el secreto es corto, o si es el
     *                               marcador de desarrollo con un perfil de
     *                               produccion activo
     */
    static void validarSecreto(String secreto, Collection<String> perfilesActivos) {
        int bytes = secreto.getBytes(StandardCharsets.UTF_8).length;

        if (bytes < BYTES_MINIMOS) {
            throw new IllegalStateException(
                    "app.jwt.secret debe tener al menos " + BYTES_MINIMOS
                            + " bytes (256 bits) para firmar con HS256; el configurado tiene "
                            + bytes + ". Defina la variable de entorno JWT_SECRET con un valor más largo.");
        }

        if (SECRETO_DE_DESARROLLO.equals(secreto) && esProduccion(perfilesActivos)) {
            throw new IllegalStateException(
                    "Define la variable JWT_SECRET en producción; no se permite el secreto de "
                            + "desarrollo. El valor por omisión de app.jwt.secret está en el "
                            + "repositorio, así que cualquiera que lo lea puede firmar tokens "
                            + "válidos para este despliegue. Perfiles activos: "
                            + perfilesActivos + ".");
        }
    }

    /** Compara sin distinguir mayusculas: el perfil lo escribe una persona. */
    private static boolean esProduccion(Collection<String> perfilesActivos) {
        for (String perfil : perfilesActivos) {
            if (perfil != null && PERFILES_DE_PRODUCCION.contains(perfil.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** Firma los tokens que emite {@code POST /api/auth/login}. */
    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(claveSimetrica));
    }

    /**
     * Verifica firma y expiracion de los tokens que llegan en la cabecera
     * {@code Authorization: Bearer ...}. Al fijar el algoritmo a HS256 se
     * descarta tambien el token manipulado que declare otro {@code alg}.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(claveSimetrica)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
