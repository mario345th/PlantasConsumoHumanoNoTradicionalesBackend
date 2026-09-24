-- V5__roles_y_usuario_rol_prueba.sql
-- Roles y asignaciones de prueba para desarrollo. NO son datos reales del sistema.
--
-- ON CONFLICT DO NOTHING en ambos INSERT: esta migracion se escribio pensando
-- en una base de datos recien creada, pero en desarrollo es comun que alguien
-- ya haya insertado manualmente un rol llamado ADMIN o INVESTIGADOR (por
-- ejemplo probando el nuevo POST /api/roles) antes de que Flyway llegue a
-- correr este script. Sin el ON CONFLICT, ese INSERT normal revienta con
-- "duplicate key value violates unique constraint rol_nombre_key" y deja la
-- migracion marcada como fallida en flyway_schema_history.

INSERT INTO rol (nombre, descripcion, activo)
VALUES
    ('ADMIN', 'Acceso total al sistema', true),
    ('INVESTIGADOR', 'Acceso de consulta y registro de datos cientificos', true)
ON CONFLICT (nombre) DO NOTHING;

SELECT setval(pg_get_serial_sequence('rol', 'id_rol'),
              (SELECT MAX(id_rol) FROM rol));

INSERT INTO usuario_rol (id_usuario, id_rol, fecha_asignacion)
SELECT u.id_usuario, r.id_rol, now()
FROM usuario u
JOIN rol r ON (
    (u.nombre_usuario = 'admin' AND r.nombre = 'ADMIN')
    OR (u.nombre_usuario = 'investigador' AND r.nombre = 'INVESTIGADOR')
)
ON CONFLICT (id_usuario, id_rol) DO NOTHING;
