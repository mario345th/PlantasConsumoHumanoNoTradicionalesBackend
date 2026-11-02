-- V13__asignar_administrador_a_admin.sql
--
-- Secuela de V12. Al quitar el rol huerfano ADMIN, el usuario de prueba
-- 'admin' (el de V3) se quedo sin ningun rol: ADMIN era el unico que tenia,
-- porque se lo asigno V5 cuando todavia no existia ADMINISTRADOR. Podia
-- iniciar sesion, pero con la lista de roles vacia y sin un solo permiso.
--
-- Aqui se le da ADMINISTRADOR, que es el rol que el diseño reserva para eso y
-- el que V9 ya le habia dado a 'admin.demo'. No se crea ningun rol ni permiso:
-- solo se agrega una fila a usuario_rol.
--
-- Los roles QA_ROL_VIGENTE y QA_ROL_BAJA no se tocan, por decision del equipo.
-- No vienen del seed --los creo QA a mano, con sus permisos QA_*-- y sus
-- pruebas manuales dependen de ellos, asi que la tabla rol queda con cuatro
-- filas: los dos del diseño mas esos dos de QA.
--
-- Resuelve por valor natural (usuario.nombre_usuario y rol.nombre) y no por
-- id, igual que V9 y V12: los ids son IDENTITY y no coinciden entre bases.
--
-- Idempotente por dos vias: el ON CONFLICT cubre que la fila ya exista, y los
-- JOIN hacen que la migracion no haga nada --sin fallar-- en una base donde no
-- esten 'admin' o ADMINISTRADOR.
--
-- fecha_asignacion no se escribe: la pone el DEFAULT de la columna, el mismo
-- criterio de V9.
--
-- AVISO: 'admin' es un usuario de prueba de V3 y su clave_hash es el mismo
-- hash que el de 'investigador', o sea que los dos comparten una clave de
-- desarrollo que esta en el repositorio. Con esta fila ese usuario pasa a
-- tener los catorce permisos, incluido USUARIO_GESTIONAR. Es aceptable en
-- local; en cualquier ambiente que no sea local estos usuarios no deberian
-- existir, igual que ya lo advierte V9 sobre 'admin.demo'.

INSERT INTO usuario_rol (id_usuario, id_rol)
SELECT u.id_usuario, r.id_rol
FROM usuario u
         JOIN rol r ON r.nombre = 'ADMINISTRADOR'
WHERE u.nombre_usuario = 'admin'
ON CONFLICT (id_usuario, id_rol) DO NOTHING;
