package sv.edu.ues.fmp.flora.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import sv.edu.ues.fmp.flora.entity.Rol;
import sv.edu.ues.fmp.flora.entity.UsuarioRol;
import sv.edu.ues.fmp.flora.entity.enums.UsuarioRolId;

@Repository
public interface UsuarioRolRepository extends JpaRepository<UsuarioRol, UsuarioRolId> {

    @Query("SELECT ur.rol FROM UsuarioRol ur WHERE ur.usuario.idUsuario = :idUsuario")
    List<Rol> findRolesByUsuarioId(@Param("idUsuario") Long idUsuario);

    /**
     * Cuantas filas de {@code usuario_rol} le dan a este usuario el rol
     * indicado, contando solo si el rol esta vigente (SEG-H09).
     * <p>
     * La clave primaria de {@code usuario_rol} es (id_usuario, id_rol) y el
     * nombre del rol es unico, asi que el resultado solo puede ser 0 o 1; se
     * devuelve el conteo y no un booleano para no depender de como traduzca
     * Hibernate una comparacion en el SELECT.
     * <p>
     * El filtro por {@code rol.activo} es el mismo criterio que ya usa
     * {@code obtenerPermisosEfectivosDeUsuario}: un rol dado de baja no otorga
     * nada, asi que quien lo tenga asignado no es administrador para ningun
     * efecto.
     *
     * @param nombreRol nombre del rol tal como esta en la tabla; se compara en
     *                  mayusculas para no depender de como se escribio
     */
    @Query("""
            SELECT count(ur) FROM UsuarioRol ur
            WHERE ur.usuario.idUsuario = :idUsuario
              AND upper(ur.rol.nombre) = upper(:nombreRol)
              AND ur.rol.activo = true
            """)
    long contarRolActivoDeUsuario(
            @Param("idUsuario") Long idUsuario,
            @Param("nombreRol") String nombreRol);

    /**
     * Cuantos usuarios ACTIVOS tienen el rol indicado vigente, sin contar a
     * uno (SEG-H09).
     *
     * <h2>Para que</h2>
     * Es la cuenta del invariante R5 de {@code ProteccionDeAdministracion}:
     * cuantos administradores activos quedarian si se desactivara al usuario
     * excluido, o si se le quitara el rol. Por eso la exclusion va en la
     * consulta y no se resta despues: la pregunta es sobre los demas.
     * <p>
     * {@code DISTINCT} sobre el id del usuario porque un mismo usuario podria
     * llegar por mas de una fila si algun dia hubiera dos roles que contaran
     * como administracion; con un solo nombre de rol es redundante, pero no
     * cuesta nada y evita que la cuenta se infle si la llamada cambia.
     */
    @Query("""
            SELECT count(DISTINCT ur.usuario.idUsuario) FROM UsuarioRol ur
            WHERE upper(ur.rol.nombre) = upper(:nombreRol)
              AND ur.rol.activo = true
              AND ur.usuario.activo = true
              AND ur.usuario.idUsuario <> :idExcluido
            """)
    long contarUsuariosActivosConRolActivoExcepto(
            @Param("nombreRol") String nombreRol,
            @Param("idExcluido") Long idExcluido);

    /**
     * Valor de {@code idExcluido} que no excluye a nadie.
     * <p>
     * {@code id_usuario} es IDENTITY y arranca en 1 --ninguna migracion
     * inserta ids a mano-- asi que {@code id_usuario <> 0} es cierto para
     * todas las filas.
     */
    long SIN_EXCLUIR = 0L;

    /**
     * Cuantos usuarios ACTIVOS tienen el rol indicado vigente, contandolos
     * todos (SEG-H09).
     *
     * <h2>Para que</h2>
     * Es el mismo invariante R5 de {@code ProteccionDeAdministracion} visto
     * desde el rol y no desde un usuario: cuando lo que se desactiva es el rol
     * ADMINISTRADOR mismo --{@code DELETE /api/roles/{id}}-- la operacion se lo
     * quita a todos a la vez, asi que no hay a quien excluir y la pregunta es
     * cuantos administradores activos hay <em>ahora</em>; si hay alguno, la
     * baja del rol los deja en cero.
     *
     * <h2>Por que delega en la consulta de arriba</h2>
     * Para que el criterio de "administrador activo" --rol vigente y cuenta
     * activa, comparando el nombre en mayusculas-- siga escrito una sola vez.
     * Una segunda {@code @Query} casi igual es justo la clase de duplicado que
     * se desincroniza cuando el criterio cambia.
     */
    default long contarUsuariosActivosConRolActivo(String nombreRol) {
        return contarUsuariosActivosConRolActivoExcepto(nombreRol, SIN_EXCLUIR);
    }
}
