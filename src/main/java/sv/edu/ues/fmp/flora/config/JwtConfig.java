package sv.edu.ues.fmp.flora.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 */
@Configuration
public class JwtConfig {

    /**
     * Minimo que exige HS256: 256 bits, es decir 32 bytes. Nimbus rechaza una
     * clave mas corta, pero lo hace al emitir el primer token, no al arrancar;
     * validarlo aqui convierte "el login devuelve 500 en produccion" en "la
     * aplicacion no arranca y dice por que".
     */
    private static final int BYTES_MINIMOS = 32;

    private final SecretKey claveSimetrica;

    public JwtConfig(@Value("${app.jwt.secret}") String secreto) {
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);

        if (bytes.length < BYTES_MINIMOS) {
            throw new IllegalStateException(
                    "app.jwt.secret debe tener al menos " + BYTES_MINIMOS
                            + " bytes (256 bits) para firmar con HS256; el configurado tiene "
                            + bytes.length + ". Defina la variable de entorno JWT_SECRET con un valor más largo.");
        }

        this.claveSimetrica = new SecretKeySpec(bytes, "HmacSHA256");
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
