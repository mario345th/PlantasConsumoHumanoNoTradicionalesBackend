-- V12__quitar_roles_fuera_del_diseno.sql
--
-- Por que V12 y no V10: en el arbol de esta rama las migraciones llegan hasta
-- V9, pero main ya tiene V10 (indices unicos url video) y V11 (indice parcial
-- ubicacion especie), y las dos estan aplicadas en la base de desarrollo
-- compartida. Numerar esta como V10 chocaria con un checksum ajeno en cuanto la
-- rama se integre --y de hecho falla ya, contra la base de desarrollo.
--
-- Alinea la tabla rol con la guia de tuplas del proyecto, que contempla dos
-- roles internos y nada mas: ADMINISTRADOR e INVESTIGADOR. Se van los otros
-- tres que habian quedado en la base:
--
--   EDITOR     -- lo sembro V9; nunca se asigno a ningun usuario.
--   CONSULTOR  -- lo sembro V9; nunca se asigno a ningun usuario.
--   ADMIN      -- lo sembro V5, antes de que existiera ADMINISTRADOR. Es el
--                 rol huerfano: V9 no le dio ni un permiso, pero V5 si se lo
--                 asigno al usuario de prueba 'admin'.
--
-- Esta migracion corrige datos, no el esquema: no agrega, quita ni modifica
-- ninguna columna, indice o restriccion.
--
-- NO SE BORRA NINGUN PERMISO. Los catorce codigos que sembro V9 siguen en uso
-- en los @PreAuthorize de los controladores; lo unico que desaparece son las
-- filas de rol_permiso que los ataban a estos tres roles. ESPECIE_PUBLICAR,
-- por ejemplo, se queda en la tabla permiso aunque EDITOR era el unico rol que
-- lo traia ademas de ADMINISTRADOR.
--
-- Resuelve por NOMBRE y no por id a proposito: id_rol es IDENTITY y V5 y V9
-- insertaron con ON CONFLICT DO NOTHING, asi que el numero que le toco a cada
-- rol depende de lo que cada base ya tuviera cuando corrio la migracion. El
-- nombre si es estable: rol.nombre tiene UNIQUE (rol_nombre_key).
--
-- Idempotente: los tres son DELETE ... WHERE nombre IN (...). Correrla sobre
-- una base donde ya no estan esos roles borra cero filas y no falla, que es lo
-- que necesita Flyway si alguien tiene que repetir el historial.
--
-- AVISO SOBRE LOS USUARIOS DE PRUEBA: el paso 2 deja al usuario 'admin' (el de
-- V3) sin ningun rol, porque ADMIN era el unico que tenia. No se borra el
-- usuario: eso es decision del equipo, y mientras tanto puede iniciar sesion y
-- recibir un token con la lista de roles vacia. El que si sirve para
-- administrar es 'admin.demo', de V9, con ADMINISTRADOR. 'investigador' y
-- 'investigador.demo' conservan INVESTIGADOR y no cambian.


-- =====================================================================
-- 1. rol_permiso
-- =====================================================================
-- Primero los permisos de esos roles: rol_permiso.id_rol es llave foranea
-- hacia rol, asi que borrar el rol antes que sus filas aqui violaria la
-- restriccion. Son las 5 filas de EDITOR y la 1 de CONSULTOR que sembro V9;
-- ADMIN no tiene ninguna, pero se incluye para que la lista de los tres roles
-- sea la misma en los tres pasos.

DELETE FROM rol_permiso
WHERE id_rol IN (
    SELECT id_rol FROM rol
    WHERE nombre IN ('EDITOR', 'CONSULTOR', 'ADMIN')
);


-- =====================================================================
-- 2. usuario_rol
-- =====================================================================
-- La otra llave foranea hacia rol. Por el seed aqui solo cae la asignacion
-- ('admin', ADMIN) de V5, pero el DELETE va por el rol y no por el usuario: si
-- en una base de desarrollo alguien le dio EDITOR o CONSULTOR a alguien a
-- mano, esa fila tambien se va, que es lo que tiene que pasar para que el
-- paso 3 no choque con la restriccion.

DELETE FROM usuario_rol
WHERE id_rol IN (
    SELECT id_rol FROM rol
    WHERE nombre IN ('EDITOR', 'CONSULTOR', 'ADMIN')
);


-- =====================================================================
-- 3. rol
-- =====================================================================
-- Ya sin nada que los referencie. Quedan ADMINISTRADOR e INVESTIGADOR.

DELETE FROM rol
WHERE nombre IN ('EDITOR', 'CONSULTOR', 'ADMIN');
