package sv.edu.ues.fmp.flora.service;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import sv.edu.ues.fmp.flora.exception.CredencialesInvalidasException;

/**
 * Quien esta llamando, segun el token y nada mas.
 *
 * <h2>El agujero que cierra</h2>
 * El flujo editorial de especie tomaba al responsable de donde lo dijera el
 * cliente: {@code creadaPor} venia en el cuerpo del POST y
 * {@code idValidador} / {@code idPublicador} en la query de los PATCH. Es
 * decir que cualquiera con el permiso correspondiente podia atribuirle la
 * creacion, la validacion o la publicacion de una ficha a otro usuario --o
 * firmar en nombre de un administrador-- sin que nada lo impidiera. La
 * trazabilidad de la tabla {@code especie} (creada_por, validada_por,
 * publicada_por) no valia como registro de quien hizo que.
 * <p>
 * Ahora el responsable sale siempre de aqui, que lee el {@code SecurityContext}
 * y no acepta ningun dato del cliente. Los campos del cuerpo y los parametros
 * de query desaparecieron: ver {@code EspecieRequest} y
 * {@code EspecieController}.
 *
 * <h2>De donde sale el id</h2>
 * De {@code authentication.getName()}, que para un token JWT es el claim
 * {@code sub}, y {@code JwtServiceImpl} pone ahi el id del usuario. Es la
 * misma fuente que ya usaba el guard de
 * {@code PUT /api/usuarios/{id}/clave} ({@code #id.toString() ==
 * authentication.name}), asi que no se inventa un segundo criterio de
 * identidad.
 * <p>
 * No se consulta la base: esta clase resuelve el id, y es el servicio que lo
 * usa el que decide si ese usuario tiene que existir --y responde 404 si
 * no-- igual que hacia con el id que llegaba del cliente.
 *
 * <h2>Por que 401 y no 500 cuando no hay nadie</h2>
 * En las rutas donde se usa, llegar sin autenticar es imposible: todas exigen
 * un permiso por {@code @PreAuthorize} y antes de eso la matriz de
 * {@code SecurityConfig} ya corto al anonimo. La rama existe para el dia en
 * que alguien abra una de esas rutas sin darse cuenta: lo que corresponde
 * entonces es pedir credenciales (401), no un error interno.
 */
@Component
public class UsuarioAutenticado {

    /**
     * Id del usuario del token.
     *
     * @throws CredencialesInvalidasException si la peticion no viene
     *                                        autenticada, o si el {@code sub}
     *                                        del token no es un id
     */
    public Long id() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();

        if (autenticacion == null
                || !autenticacion.isAuthenticated()
                || autenticacion instanceof AnonymousAuthenticationToken) {

            throw new CredencialesInvalidasException(
                    "Esta operación requiere un usuario autenticado");
        }

        try {
            return Long.valueOf(autenticacion.getName());
        } catch (NumberFormatException noEsUnId) {
            // El token verifica firma y vigencia, pero el sub lo puso quien lo
            // emitio: si algun dia se emite un token con el nombre de usuario
            // en el sub en vez del id, esto tiene que fallar de forma clara y
            // no guardar una ficha a nombre de nadie.
            throw new CredencialesInvalidasException(
                    "El token no identifica al usuario con un id válido");
        }
    }
}
