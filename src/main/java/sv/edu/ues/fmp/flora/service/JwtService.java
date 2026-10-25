package sv.edu.ues.fmp.flora.service;

import java.time.LocalDateTime;
import java.util.List;

import sv.edu.ues.fmp.flora.dto.response.UsuarioResponse;

/**
 * Emision de los tokens de acceso.
 * <p>
 * Solo emite. La verificacion del token que llega en cada peticion la hace el
 * {@code JwtDecoder} de {@code JwtConfig} dentro de la cadena de filtros de
 * Spring Security, no esta clase.
 */
public interface JwtService {

    /**
     * Token recien firmado junto con el momento en que caduca, para no tener
     * que volver a decodificarlo solo para saber hasta cuando vale.
     */
    record TokenEmitido(String token, LocalDateTime expiraEn) {
    }

    /**
     * Firma un token para el usuario ya autenticado.
     *
     * @param usuario  usuario validado; de aqui salen los claims {@code sub}
     *                 (su id) y {@code nombreUsuario}
     * @param roles    nombres de los roles vigentes, por ejemplo
     *                 {@code ["ADMIN"]}; van al claim {@code roles}
     * @param permisos codigos de los permisos efectivos; van al claim
     *                 {@code permisos}
     */
    TokenEmitido emitir(UsuarioResponse usuario, List<String> roles, List<String> permisos);
}
