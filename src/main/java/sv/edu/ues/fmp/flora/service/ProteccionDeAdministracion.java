package sv.edu.ues.fmp.flora.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.entity.Rol;
import sv.edu.ues.fmp.flora.entity.Usuario;
import sv.edu.ues.fmp.flora.exception.EstadoInvalidoException;
import sv.edu.ues.fmp.flora.exception.OperacionNoPermitidaException;
import sv.edu.ues.fmp.flora.repository.UsuarioRepository;
import sv.edu.ues.fmp.flora.repository.UsuarioRolRepository;

/**
 * Las cinco reglas que protegen el acceso de administracion (SEG-H09).
 *
 * <h2>Que agujero cierra</h2>
 * El permiso {@code USUARIO_GESTIONAR} abre todo el modulo de seguridad, y eso
 * dejaba tres cosas posibles para cualquiera que lo tuviera:
 * <ul>
 *   <li><strong>Auto-promocion.</strong> Asignarse a si mismo el rol
 *       ADMINISTRADOR con un {@code POST /api/usuario-roles}. Un usuario de
 *       gestion se volvia administrador sin que nadie lo autorizara, que es la
 *       misma escalacion vertical que se cerro a nivel de endpoint, solo que
 *       un paso mas adentro.</li>
 *   <li><strong>Golpe entre administradores.</strong> Quitarle el rol o dar de
 *       baja a otro administrador --incluido el unico que quedara-- sin ser
 *       nadie en particular.</li>
 *   <li><strong>Quedarse sin administradores.</strong> Nada impedia dejar el
 *       sistema con cero administradores activos; a partir de ahi la
 *       administracion solo se recupera por SQL.</li>
 * </ul>
 * Se sostiene en {@code usuario.es_superadmin}, la marca que crea {@code V19} y
 * que ninguna ruta de la API escribe: el superadministrador se designa por
 * migracion.
 *
 * <h2>Las reglas</h2>
 * <table border="1">
 *   <caption>Datos: <em>quien llama</em> es el usuario del token y
 *   <em>objetivo</em> el usuario afectado</caption>
 *   <tr><th>Regla</th><th>Dice</th><th>Respuesta</th></tr>
 *   <tr><td>R1 Autoproteccion</td>
 *       <td>Nadie se desactiva a si mismo ni se quita su propio
 *           ADMINISTRADOR.</td><td>403</td></tr>
 *   <tr><td>R2 Solo el superadmin asigna</td>
 *       <td>Solo el superadministrador ASIGNA ADMINISTRADOR. Es lo que cierra
 *           la auto-promocion: con {@code USUARIO_GESTIONAR} ya no alcanza
 *           para volverse admin, ni a si mismo ni a un comparsa.</td>
 *       <td>403</td></tr>
 *   <tr><td>R3 Solo el superadmin quita</td>
 *       <td>Solo el superadministrador QUITA ADMINISTRADOR o desactiva a un
 *           administrador; un admin normal no puede con otro admin.</td>
 *       <td>403</td></tr>
 *   <tr><td>R4 Superadmin protegido</td>
 *       <td>A el nadie le quita el rol ni lo desactiva.</td><td>403</td></tr>
 *   <tr><td>R5 Nunca cero admins</td>
 *       <td>Ninguna operacion deja el sistema con cero administradores
 *           activos.</td><td>409</td></tr>
 * </table>
 *
 * <h2>El orden importa</h2>
 * Primero R1 y R4, que son prohibiciones absolutas y valen incluso para el
 * superadministrador: asi el mensaje que recibe el superadmin que intenta
 * desactivarse es "no puede desactivar su propia cuenta" y no un 409 sobre el
 * numero de administradores. Luego R2 y R3, que son la pregunta de quien
 * llama. R5 al final: no tiene sentido contar administradores en una operacion
 * que de todas formas no estaba permitida, y es la unica que responde 409.
 *
 * <h2>Por que aqui y no en {@code @PreAuthorize}</h2>
 * Las reglas no se contestan con las autoridades del token: necesitan la marca
 * {@code es_superadmin} de la base, los roles vigentes del objetivo y, en R5,
 * una cuenta sobre toda la tabla. Method-security tendria que consultar la base
 * desde una expresion SpEL, y el resultado quedaria repartido entre tres
 * anotaciones en vez de leerse de corrido.
 * <p>
 * Y por que un componente y no codigo dentro de cada servicio: las mismas
 * reglas las aplican {@code UsuarioRolServiceImpl.asignar} y {@code .quitar}
 * --modulo usuario-rol--, {@code UsuarioServiceImpl.desactivar} --modulo
 * usuario-- y {@code RolServiceImpl.desactivar} --modulo rol--. Repetirlas en
 * cada sitio es como se abren los huecos, y la cuarta via fue exactamente eso:
 * {@code DELETE /api/roles/{id}} sobre el rol ADMINISTRADOR lo desactivaba
 * para todos --dejando el sistema sin administradores-- sin pasar por ninguna
 * de estas reglas. Cerrarla fue una linea llamando aqui
 * ({@link #verificarDesactivacionDeRol(Rol)}) y no reescribir el criterio.
 *
 * <h2>Lo que no decide</h2>
 * Si quien llama tiene derecho a tocar el modulo de seguridad: eso ya lo
 * resolvio {@code USUARIO_GESTIONAR} en el {@code @PreAuthorize} del
 * controlador. Aqui se asume que el permiso esta y se pregunta otra cosa: si
 * <em>este</em> usuario puede hacerle <em>esto</em> a <em>ese</em> otro.
 */
@Component
@RequiredArgsConstructor
public class ProteccionDeAdministracion {

    /**
     * Nombre del rol de administracion en la tabla {@code rol}, tal como lo
     * dejo {@code V12}. Se compara por nombre y no por id porque
     * {@code id_rol} es IDENTITY y no vale lo mismo en dos bases; el nombre si,
     * que ademas es unico ({@code rol_nombre_key}).
     */
    public static final String ADMINISTRADOR = "ADMINISTRADOR";

    private final UsuarioAutenticado usuarioAutenticado;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioRolRepository usuarioRolRepository;

    /**
     * Antes de asignar un rol a un usuario.
     * <p>
     * Solo mira el caso del rol ADMINISTRADOR; cualquier otro rol se asigna
     * como siempre, porque lo que esta tarea protege es el acceso de
     * administracion y no la gestion de roles en general.
     *
     * @param objetivo usuario que recibiria el rol
     * @param rol      rol que se le asignaria
     * @throws OperacionNoPermitidaException R2: quien llama no es el
     *                                       superadministrador
     */
    public void verificarAsignacionDeRol(Usuario objetivo, Rol rol) {
        if (!esElRolDeAdministrador(rol)) {
            return;
        }

        // R2. No hay excepcion para el caso "me lo asigno a mi mismo": ese es
        // justamente el que se esta cerrando, y cae aqui como cualquier otro.
        if (!quienLlamaEsSuperadmin()) {
            throw new OperacionNoPermitidaException(
                    "Solo el superadministrador puede asignar el rol " + ADMINISTRADOR
                            + "; el usuario " + objetivo.getNombreUsuario()
                            + " no puede ser promovido desde esta cuenta");
        }
    }

    /**
     * Antes de quitarle un rol a un usuario.
     *
     * @param objetivo usuario al que se le quitaria el rol
     * @param rol      rol que se le quitaria
     * @throws OperacionNoPermitidaException R1, R4 o R3
     * @throws EstadoInvalidoException       R5: seria el ultimo administrador
     *                                       activo
     */
    public void verificarQuitarRol(Usuario objetivo, Rol rol) {
        if (!esElRolDeAdministrador(rol)) {
            return;
        }

        Long idQuienLlama = usuarioAutenticado.id();

        // R1.
        if (idQuienLlama.equals(objetivo.getIdUsuario())) {
            throw new OperacionNoPermitidaException(
                    "No puede quitarse a si mismo el rol " + ADMINISTRADOR
                            + "; pidalo al superadministrador");
        }

        // R4.
        if (esSuperadmin(objetivo)) {
            throw new OperacionNoPermitidaException(
                    "El usuario " + objetivo.getNombreUsuario()
                            + " es el superadministrador: no se le puede quitar el rol "
                            + ADMINISTRADOR);
        }

        // R3.
        if (!esSuperadmin(quienLlama(idQuienLlama))) {
            throw new OperacionNoPermitidaException(
                    "Solo el superadministrador puede quitar el rol " + ADMINISTRADOR
                            + "; un administrador no puede hacerlo con otro");
        }

        // R5.
        verificarQueNoEsElUltimoAdministrador(objetivo,
                "No se puede quitar el rol " + ADMINISTRADOR + " al usuario "
                        + objetivo.getNombreUsuario()
                        + " porque es el ultimo administrador activo del sistema");
    }

    /**
     * Antes de dar de baja logica a un usuario.
     * <p>
     * R1 se aplica a cualquier usuario y no solo a los administradores: nadie
     * se desactiva a si mismo. Las demas reglas solo entran cuando el objetivo
     * es administrador; dar de baja a un investigador sigue siendo una
     * operacion normal de {@code USUARIO_GESTIONAR}.
     *
     * @param objetivo usuario que se desactivaria
     * @throws OperacionNoPermitidaException R1, R4 o R3
     * @throws EstadoInvalidoException       R5: seria el ultimo administrador
     *                                       activo
     */
    public void verificarDesactivacion(Usuario objetivo) {
        Long idQuienLlama = usuarioAutenticado.id();

        // R1. Vale para todos: un administrador que se desactiva a si mismo
        // deja ademas un rol ADMINISTRADOR asignado que no otorga nada, que es
        // el estado mas confuso en el que se puede quedar la tabla.
        if (idQuienLlama.equals(objetivo.getIdUsuario())) {
            throw new OperacionNoPermitidaException(
                    "No puede desactivar su propia cuenta; pidalo a otro administrador");
        }

        // R4.
        if (esSuperadmin(objetivo)) {
            throw new OperacionNoPermitidaException(
                    "El usuario " + objetivo.getNombreUsuario()
                            + " es el superadministrador: su cuenta no se puede desactivar");
        }

        // R3. Solo pesa si el objetivo es administrador; si no lo es, cualquier
        // gestor de usuarios puede darlo de baja como hasta ahora.
        if (esAdministrador(objetivo) && !esSuperadmin(quienLlama(idQuienLlama))) {
            throw new OperacionNoPermitidaException(
                    "Solo el superadministrador puede desactivar al administrador "
                            + objetivo.getNombreUsuario());
        }

        // R5.
        verificarQueNoEsElUltimoAdministrador(objetivo,
                "No se puede desactivar al usuario " + objetivo.getNombreUsuario()
                        + " porque es el ultimo administrador activo del sistema");
    }

    /**
     * Antes de dar de baja logica un rol.
     *
     * <h2>Que via cierra</h2>
     * {@code DELETE /api/roles/{id}} sobre el rol ADMINISTRADOR. Era la sexta
     * via a "cero administradores" y la unica que no pasaba por ninguna de las
     * reglas de arriba: no le quita el rol a nadie --las filas de
     * {@code usuario_rol} siguen ahi-- pero lo deja {@code activo = false}, y
     * un rol dado de baja no otorga nada
     * ({@code obtenerPermisosEfectivosDeUsuario} lo salta, y
     * {@link #esAdministrador(Usuario)} tampoco lo cuenta). El efecto es el de
     * degradar a todos los administradores de una sola peticion.
     *
     * <h2>Solo el invariante, y por que</h2>
     * De las cinco reglas aqui aplica R5 y nada mas. R1 y R4 hablan de un
     * usuario objetivo y aqui no hay ninguno; R2 y R3 preguntan quien llama,
     * y responderlas tambien por esta via seria reservar al superadmin la
     * edicion del catalogo de roles --una decision distinta de la que esta
     * tarea cierra--. El agujero concreto es el estado al que lleva la
     * operacion, y eso es exactamente R5: 409, no 403.
     *
     * @param rol rol que se desactivaria
     * @throws EstadoInvalidoException R5: el sistema quedaria sin
     *                                 administradores activos
     */
    public void verificarDesactivacionDeRol(Rol rol) {
        if (!esElRolDeAdministrador(rol)) {
            return;
        }

        // Si el rol ya estaba dado de baja, volver a desactivarlo no cambia el
        // numero de administradores activos --que con el rol inactivo ya es
        // cero-- y el invariante no esta en juego. Es la misma salvedad que
        // verificarQueNoEsElUltimoAdministrador hace con el objetivo que ya no
        // cuenta: el 409 es para la operacion que causa el problema, no para
        // la que lo encuentra hecho.
        if (!Boolean.TRUE.equals(rol.getActivo())) {
            return;
        }

        // R5. No hay a quien excluir: la baja del rol se lo quita a todos a la
        // vez, asi que cualquier administrador activo de hoy es uno que se
        // quedaria sin el rol, y el sistema sin ninguno.
        long administradoresActivos =
                usuarioRolRepository.contarUsuariosActivosConRolActivo(ADMINISTRADOR);

        if (administradoresActivos > 0) {
            throw new EstadoInvalidoException(
                    "No se puede desactivar el rol " + ADMINISTRADOR
                            + " porque el sistema quedaria sin administradores activos");
        }
    }

    /**
     * R5, el invariante de "nunca cero administradores".
     *
     * <h2>Por que se pregunta por los demas y no por el total</h2>
     * La cuenta excluye al objetivo, asi que responde directamente a "cuantos
     * administradores activos quedarian si esta operacion se ejecutara". Mirar
     * el total antes de la operacion no sirve: con dos administradores
     * cualquiera de las dos bajas es valida, con uno ninguna, y la diferencia
     * esta en si el afectado es uno de los que se cuentan.
     *
     * <h2>Por que solo se comprueba si el objetivo cuenta hoy</h2>
     * Si el objetivo ya esta inactivo, o si su rol ADMINISTRADOR no esta
     * vigente, la operacion no baja el numero de administradores activos y el
     * invariante no esta en juego. Sin esta condicion, una base que ya
     * estuviera en cero administradores activos --que es un problema, pero no
     * uno que cause esta operacion-- devolveria 409 a operaciones inocuas, como
     * limpiar el rol de una cuenta dada de baja hace meses.
     */
    private void verificarQueNoEsElUltimoAdministrador(Usuario objetivo, String mensaje) {
        if (!cuentaComoAdministradorActivo(objetivo)) {
            return;
        }

        long otros = usuarioRolRepository.contarUsuariosActivosConRolActivoExcepto(
                ADMINISTRADOR, objetivo.getIdUsuario());

        if (otros == 0) {
            throw new EstadoInvalidoException(mensaje);
        }
    }

    /** Si el rol que se esta asignando o quitando es el de administracion. */
    private static boolean esElRolDeAdministrador(Rol rol) {
        return rol != null && ADMINISTRADOR.equalsIgnoreCase(rol.getNombre());
    }

    /**
     * Si quien llama es el superadministrador.
     * <p>
     * La marca se lee de la base y no del token a proposito: el token no lleva
     * ningun claim de superadmin --no se agrego-- asi que no hay forma de
     * falsificarla firmando un token, y si algun dia la marca se moviera, la
     * decision siguiente ya usa el valor nuevo sin esperar a que caduque
     * ningun token.
     */
    private boolean quienLlamaEsSuperadmin() {
        return esSuperadmin(quienLlama(usuarioAutenticado.id()));
    }

    /**
     * El usuario del token, si existe en la base.
     * <p>
     * Puede no existir: el {@code sub} de un token vigente apunta a una fila
     * que pudo borrarse. En ese caso no es superadmin, que es el lado seguro.
     */
    private Optional<Usuario> quienLlama(Long idQuienLlama) {
        return usuarioRepository.findById(idQuienLlama);
    }

    private static boolean esSuperadmin(Optional<Usuario> usuario) {
        return usuario.map(ProteccionDeAdministracion::esSuperadmin).orElse(false);
    }

    private static boolean esSuperadmin(Usuario usuario) {
        return usuario != null && Boolean.TRUE.equals(usuario.getEsSuperadmin());
    }

    /** Si el usuario tiene asignado el rol ADMINISTRADOR y ese rol esta vigente. */
    private boolean esAdministrador(Usuario usuario) {
        return usuarioRolRepository.contarRolActivoDeUsuario(
                usuario.getIdUsuario(), ADMINISTRADOR) > 0;
    }

    /**
     * Si el usuario es uno de los administradores que el invariante de R5
     * cuenta: cuenta activa y rol ADMINISTRADOR vigente.
     */
    private boolean cuentaComoAdministradorActivo(Usuario usuario) {
        return Boolean.TRUE.equals(usuario.getActivo()) && esAdministrador(usuario);
    }
}
