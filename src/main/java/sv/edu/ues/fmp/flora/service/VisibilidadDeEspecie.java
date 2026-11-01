package sv.edu.ues.fmp.flora.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.entity.Especie;
import sv.edu.ues.fmp.flora.entity.enums.EstadoPublicacion;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.repository.EspecieRepository;

/**
 * Decide si quien esta llamando puede ver el contenido de una especie.
 *
 * <h2>El agujero que cierra</h2>
 * Las consultas de la ficha quedaron cerradas al anonimo, pero los seis
 * sub-recursos de una especie --sus fuentes, habitats, imagenes, nombres
 * comunes, partes comestibles y videos-- seguian publicos en {@code GET} y
 * ninguno miraba el estado del padre. Con el id de un borrador se podian ver
 * sus imagenes y sus nombres comunes sin token, o sea el contenido de una
 * ficha que nadie ha publicado todavia.
 *
 * <h2>El criterio</h2>
 * El mismo que {@code GET /api/especies/publicadas/{id}}:
 * <ol>
 *   <li>Si la especie esta PUBLICADA y activa, la ve cualquiera.</li>
 *   <li>Si no, la ve quien traiga {@code ESPECIE_CONSULTAR} en el token.</li>
 *   <li>En cualquier otro caso --y tambien cuando el id no existe-- se lanza
 *       la misma {@link RecursoNoEncontradoException} con el mismo texto.</li>
 * </ol>
 * El punto tres es el que importa: si un borrador respondiera 403 y un id
 * inexistente 404, la diferencia bastaria para ir probando numeros y averiguar
 * que fichas hay en preparacion. Al responder las dos cosas igual, desde fuera
 * no se distingue "existe pero no esta publicada" de "no existe".
 * <p>
 * Por eso no se usa {@code @PreAuthorize} aqui: method-security devuelve 403, y
 * el 403 es justo la respuesta que delata. La decision tiene que estar en la
 * logica para poder contestar 404.
 *
 * <h2>Por que no lo llama tambien obtenerPublicadaPorId</h2>
 * Ese endpoint es mas estricto a proposito: es el detalle publico y solo
 * resuelve fichas publicadas, sin la salida del punto dos, porque para leer un
 * borrador ya existe {@code GET /api/especies/{id}}. Lo que si comparten es el
 * texto del 404, via {@link #noEncontrada(Long)}, para que los dos sean
 * indistinguibles de un id inexistente y para que no se separen con el tiempo.
 */
@Component
@RequiredArgsConstructor
public class VisibilidadDeEspecie {

    /** Codigo del permiso, tal como lo siembra {@code V9} y lo mapea el converter: sin prefijo. */
    private static final String ESPECIE_CONSULTAR = "ESPECIE_CONSULTAR";

    private final EspecieRepository especieRepository;

    /**
     * Corta la peticion si quien llama no deberia ver esta especie.
     * <p>
     * Se llama al principio de cada {@code GET} de sub-recurso, antes de leer
     * nada del hijo. Cuesta una consulta por el padre que se suma a la que el
     * servicio ya hace para comprobar que existe; es el precio de decidir con
     * el estado en mano y a este volumen no se nota.
     *
     * @param idEspecie el padre al que cuelga el sub-recurso
     * @throws RecursoNoEncontradoException si la especie no existe, o si no
     *                                      esta publicada y quien llama no
     *                                      trae {@code ESPECIE_CONSULTAR}
     */
    public void verificarQuePuedeVer(Long idEspecie) {
        Especie especie = especieRepository.findById(idEspecie)
                .orElseThrow(() -> noEncontrada(idEspecie));

        if (estaPublicada(especie) || puedeConsultarFichasInternas()) {
            return;
        }

        throw noEncontrada(idEspecie);
    }

    /**
     * El 404 de "especie no encontrada", en un solo sitio.
     * <p>
     * El texto tiene que ser identico al de un id inexistente, que es lo que
     * hace que el borrador no se pueda distinguir. Lo usan este componente y
     * {@code EspecieServiceImpl.obtenerPublicadaPorId}.
     */
    public static RecursoNoEncontradoException noEncontrada(Long idEspecie) {
        return new RecursoNoEncontradoException("No existe una especie con id " + idEspecie);
    }

    /** Publicada y activa, el mismo par que exige la consulta del detalle publico. */
    private static boolean estaPublicada(Especie especie) {
        return especie.getEstadoPublicacion() == EstadoPublicacion.PUBLICADA
                && Boolean.TRUE.equals(especie.getActiva());
    }

    /**
     * Si el token de quien llama trae {@code ESPECIE_CONSULTAR}.
     * <p>
     * Se mira la autoridad y no si esta autenticado: al anonimo Spring Security
     * le da un {@code AnonymousAuthenticationToken} que responde {@code true} a
     * {@code isAuthenticated()}, asi que preguntar eso no distinguiria nada. Lo
     * que lo distingue es que sus autoridades son {@code ROLE_ANONYMOUS} y
     * nunca el permiso.
     */
    private static boolean puedeConsultarFichasInternas() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null) {
            return false;
        }

        for (GrantedAuthority autoridad : autenticacion.getAuthorities()) {
            if (ESPECIE_CONSULTAR.equals(autoridad.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
