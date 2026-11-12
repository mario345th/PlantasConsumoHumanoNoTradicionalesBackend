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
 *   <li><strong>El secreto de desarrollo solo vale en desarrollo.</strong> El
 *       valor por omision de {@code app.jwt.secret} esta commiteado, asi que
 *       cualquiera que lea el repositorio puede firmar tokens validos para
 *       cualquier despliegue que lo siga usando. Si el secreto resuelto es el
 *       marcador y el ambiente no es de desarrollo, la aplicacion no
 *       arranca.</li>
 * </ol>
 *
 * <h2>Que cuenta como desarrollo (SEG-PB-02)</h2>
 * La comprobacion es una LISTA BLANCA, no una lista de ambientes prohibidos.
 * La primera version miraba si habia un perfil {@code prod},
 * {@code produccion} o {@code production} activo, y eso dejaba el agujero
 * abierto para cualquier otro nombre: un despliegue con el perfil {@code qa},
 * {@code staging}, {@code uat} o {@code docker} firmaba con la clave del
 * repositorio y nada avisaba. Invertida la logica, el marcador se acepta
 * unicamente cuando:
 * <ul>
 *   <li>no hay ningun perfil activo, que es como se levanta el proyecto en la
 *       maquina de cada quien y como corren las pruebas; o</li>
 *   <li>TODOS los perfiles activos estan en {@link #PERFILES_DE_DESARROLLO}.
 *       Basta un perfil que no sea de desarrollo --aunque venga acompañado de
 *       {@code dev}-- para exigir un {@code JWT_SECRET} propio: si algo que no
 *       es local esta activo, el despliegue ya no es solo el de la maquina de
 *       quien escribe el codigo.</li>
 * </ul>
 * Cualquier otro ambiente tiene que definir {@code JWT_SECRET} con un valor
 * propio. Es configuracion del despliegue; aqui solo esta la guarda que
 * impide arrancar sin ella.
 * <p>
 * El caso "sin ningun perfil activo" es deliberadamente permisivo: nadie
 * tiene que configurar nada para levantar el proyecto en su maquina. La
 * contrapartida, dicha para que no sorprenda: un despliegue que olvide
 * <em>tambien</em> el perfil arranca con el marcador. Cerrar eso significaria
 * exigir {@code -Dspring.profiles.active=dev} a todo el equipo y a las
 * pruebas; es una decision del equipo y, si se toma, el cambio es una linea en
 * {@link #esDesarrollo(Collection)}.
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
     * Los unicos nombres de perfil con los que se admite el secreto de
     * desarrollo.
     * <p>
     * Estan los dos idiomas porque el proyecto mezcla los dos, y
     * {@code local} y {@code test} porque son los que se usan para correr en
     * la maquina de cada quien y en las pruebas. Agregar un nombre aqui es
     * declarar "este ambiente puede firmar con la clave que esta en el
     * repositorio": no se agrega un perfil de despliegue por comodidad.
     */
    private static final Set<String> PERFILES_DE_DESARROLLO =
            Set.of("dev", "desarrollo", "local", "test");

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
     *                               marcador de desarrollo en un ambiente que
     *                               no es de desarrollo
     */
    static void validarSecreto(String secreto, Collection<String> perfilesActivos) {
        int bytes = secreto.getBytes(StandardCharsets.UTF_8).length;

        if (bytes < BYTES_MINIMOS) {
            throw new IllegalStateException(
                    "app.jwt.secret debe tener al menos " + BYTES_MINIMOS
                            + " bytes (256 bits) para firmar con HS256; el configurado tiene "
                            + bytes + ". Defina la variable de entorno JWT_SECRET con un valor más largo.");
        }

        if (SECRETO_DE_DESARROLLO.equals(secreto) && !esDesarrollo(perfilesActivos)) {
            throw new IllegalStateException(
                    "Define la variable JWT_SECRET en producción, y en cualquier otro ambiente "
                            + "que no sea de desarrollo; no se permite el secreto de "
                            + "desarrollo. El valor por omisión de app.jwt.secret está en el "
                            + "repositorio, así que cualquiera que lo lea puede firmar tokens "
                            + "válidos para este despliegue. Ambientes de desarrollo: sin "
                            + "perfil activo, o únicamente " + PERFILES_DE_DESARROLLO
                            + ". Perfiles activos: " + perfilesActivos + ".");
        }
    }

    /**
     * Si el ambiente es de desarrollo: sin perfiles, o con todos los activos en
     * {@link #PERFILES_DE_DESARROLLO}.
     * <p>
     * Compara sin distinguir mayusculas y recortando espacios porque el perfil
     * lo escribe una persona, en una variable de entorno o en la linea de
     * comandos.
     */
    private static boolean esDesarrollo(Collection<String> perfilesActivos) {
        // Sin perfil activo es el arranque local de siempre, y el de las
        // pruebas. Si esto cambiara a false, levantar el proyecto en una
        // maquina nueva exigiria configurar el perfil o el secreto.
        if (perfilesActivos.isEmpty()) {
            return true;
        }

        for (String perfil : perfilesActivos) {
            if (perfil == null
                    || !PERFILES_DE_DESARROLLO.contains(perfil.trim().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
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
