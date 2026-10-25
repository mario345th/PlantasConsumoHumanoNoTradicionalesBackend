package sv.edu.ues.fmp.flora.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import sv.edu.ues.fmp.flora.dto.response.UsuarioResponse;
import sv.edu.ues.fmp.flora.service.JwtService;

/**
 * Firma los tokens con la clave simetrica de {@code JwtConfig}.
 *
 * <h2>Las autoridades viajan dentro del token</h2>
 * Los claims {@code roles} y {@code permisos} se calculan una sola vez, al
 * iniciar sesion, y quedan congelados dentro del token hasta que expire. La
 * consecuencia hay que tenerla clara: <strong>quitarle un rol a alguien, o
 * desactivar ese rol, no surte efecto de inmediato</strong>. Durante lo que le
 * quede de vigencia al token que ya tiene en la mano, sigue entrando con las
 * autoridades de cuando lo pidio. Lo mismo al reves: un rol recien concedido
 * no aparece hasta que vuelva a iniciar sesion.
 * <p>
 * Es el canje habitual de un esquema sin estado: a cambio de no consultar la
 * base de datos en cada peticion, se acepta que las autoridades esten algo
 * rezagadas. Por eso {@code app.jwt.expiration-minutes} es corto (60): acota
 * esa ventana.
 * <p>
 * Si el equipo necesita revocacion inmediata --por ejemplo para dar de baja
 * una cuenta comprometida al instante-- hay dos caminos, y ninguno de los dos
 * esta implementado aqui: una lista de tokens revocados que el filtro
 * consulte, o resolver las autoridades contra la base de datos en cada
 * peticion en vez de leerlas del token. Las dos cuestan una consulta por
 * peticion; la primera ademas necesita donde guardar la lista. Queda para
 * discutirlo con el equipo, igual que los refresh tokens, que tambien estan
 * fuera del alcance de esta tarea.
 */
@Service
public class JwtServiceImpl implements JwtService {

    private final JwtEncoder jwtEncoder;
    private final Duration vigencia;

    /**
     * Constructor explicito en vez de {@code @RequiredArgsConstructor} porque
     * uno de los dos parametros no es un bean, sino un valor de configuracion
     * que hay que anotar con {@code @Value}.
     */
    public JwtServiceImpl(
            JwtEncoder jwtEncoder,
            @Value("${app.jwt.expiration-minutes}") Long expiracionMinutos) {

        this.jwtEncoder = jwtEncoder;
        this.vigencia = Duration.ofMinutes(expiracionMinutos);
    }

    @Override
    public TokenEmitido emitir(UsuarioResponse usuario, List<String> roles, List<String> permisos) {
        // Truncado a segundos: iat y exp son segundos desde epoch, y dejar los
        // milisegundos haria que expiraEn de la respuesta y el exp del token
        // no fueran exactamente el mismo instante.
        Instant emitidoEn = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiraEn = emitidoEn.plus(vigencia);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(usuario.getId()))
                .issuedAt(emitidoEn)
                .expiresAt(expiraEn)
                .claim("nombreUsuario", usuario.getNombreUsuario())
                .claim("roles", roles)
                // El tamaño del token crece con esta lista: cada codigo de
                // permiso son sus caracteres mas el relleno de base64, y el
                // token viaja en una cabecera en cada peticion. Medido contra
                // la base de desarrollo con un usuario de dos permisos
                // efectivos, el token queda en unos 310 caracteres, holgado
                // frente al limite habitual de 8 KB por cabecera. Si el
                // catalogo de permisos crece a varias decenas por rol hay que
                // volver a medirlo y, llegado el caso, dejar en el token solo
                // los roles y resolver los permisos contra la base.
                .claim("permisos", permisos)
                .build();

        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();

        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(cabecera, claims))
                .getTokenValue();

        return new TokenEmitido(token, LocalDateTime.ofInstant(expiraEn, ZoneId.systemDefault()));
    }
}
