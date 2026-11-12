-- V19__superadmin_y_proteccion_del_acceso_de_administracion.sql
--
-- SEG-H09. Marca al superadministrador del sistema, que es el dato que
-- necesitan las reglas nuevas de ProteccionDeAdministracion: quien puede
-- asignar o quitar el rol ADMINISTRADOR, a quien se puede dar de baja, y quien
-- no se puede tocar nunca.
--
--
-- POR QUE ES V19
-- ==============
-- V18 es la ultima version aplicada en florav1 (flyway_schema_history, version
-- 18, success = true), asi que V19 es el siguiente numero libre. Ninguna
-- migracion aplicada se modifica: reusar un numero haria fallar el arranque
-- por version repetida y editar un archivo ya aplicado le rompe el checksum.
-- Mismo criterio que siguieron V12, V17 y V18.
--
--
-- QUE HACE
-- ========
-- 1. Agrega usuario.es_superadmin BOOLEAN NOT NULL DEFAULT FALSE. Con el
--    DEFAULT, las filas que ya existen quedan en FALSE sin reescribirse y las
--    que entren despues no necesitan nombrar la columna.
--
-- 2. Pone TRUE en el usuario 'admin' --la semilla base de V3, el que V13 dejo
--    con el rol ADMINISTRADOR-- y FALSE en cualquier otro que lo tuviera, para
--    que correr el script dos veces deje siempre exactamente un superadmin.
--
-- 3. Verifica que quedo UNO SOLO en TRUE y que es 'admin'. Si no, falla y
--    Flyway revierte la migracion completa.
--
--
-- LAS DECISIONES
-- ==============
--
-- a. Se resuelve por clave natural (usuario.nombre_usuario) y no por id, igual
--    que V9, V12 y V13: id_usuario es IDENTITY y no vale lo mismo en dos
--    bases. En florav1 'admin' es el id 1, pero eso no se escribe aqui.
--
-- b. Se compara con lower(nombre_usuario) porque la unicidad del nombre de
--    usuario en el esquema es justamente un indice funcional sobre
--    lower(nombre_usuario) (uk_usuario_nombre_lower): 'Admin' y 'admin' no
--    pueden coexistir, asi que la comparacion insensible no puede casar dos
--    filas distintas.
--
-- c. NO es 'admin.demo'. Los dos tienen ADMINISTRADOR activo --'admin' por
--    V13 y 'admin.demo' por V9-- y la tarea pide la semilla base. 'admin.demo'
--    queda como administrador normal, que es lo que hace util a la pareja: es
--    el caso de prueba de "admin normal contra otro admin".
--
-- d. La marca se setea AQUI y no se expone por la API. No hay endpoint, ni
--    campo en los DTO de usuario, que la cambie: UsuarioMapper.updateEntity no
--    la toca y UsuarioResponse no la devuelve. Moverla es una migracion y una
--    decision del equipo, no una llamada HTTP.
--
-- e. No se crea un indice unico parcial para que la base impida un segundo
--    TRUE. Seria facil --CREATE UNIQUE INDEX ... ON usuario (es_superadmin)
--    WHERE es_superadmin-- pero convertiria el "uno solo" en una restriccion
--    permanente del esquema, y eso es una decision de diseño que esta tarea no
--    toma: hoy el invariante lo sostienen el bloque de verificacion de abajo y
--    el hecho de que ninguna ruta de la API escriba la columna. Queda anotado
--    por si el equipo lo quiere mas adelante.
--
-- f. El UPDATE del bloque 2 dispara trg_usuario_fecha_actualizacion y le mueve
--    fecha_actualizacion a 'admin'. Es inevitable con un UPDATE y no es un
--    dato que nadie lea; el WHERE evita tocar las filas que ya estan como
--    deben, de modo que un segundo pase no mueve ninguna fecha.


-- =====================================================================
-- 1. La columna
-- =====================================================================

ALTER TABLE usuario
    ADD COLUMN IF NOT EXISTS es_superadmin BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN usuario.es_superadmin IS
    'Marca al superadministrador del sistema. Solo el puede asignar o quitar '
    'el rol ADMINISTRADOR y desactivar a un administrador, y a el nadie puede '
    'quitarle el rol ni desactivarlo (SEG-H09). Se setea por migracion: '
    'ninguna ruta de la API escribe esta columna.';


-- =====================================================================
-- 2. El superadmin es 'admin'
-- =====================================================================

UPDATE usuario
SET es_superadmin = TRUE
WHERE lower(nombre_usuario) = 'admin'
  AND es_superadmin IS DISTINCT FROM TRUE;

-- Cualquier otro que estuviera marcado deja de estarlo. En florav1 no hay
-- ninguno --la columna nace en FALSE-- pero esto es lo que hace que el script
-- sea idempotente y que el invariante del bloque 3 no dependa del estado
-- previo de la base.
UPDATE usuario
SET es_superadmin = FALSE
WHERE lower(nombre_usuario) <> 'admin'
  AND es_superadmin;


-- =====================================================================
-- 3. Verificacion: uno solo, y es 'admin'
-- =====================================================================
-- Si falla, Flyway revierte la transaccion completa (PostgreSQL tiene DDL
-- transaccional, asi que la columna tampoco se queda a medias).
--
-- El caso de cero marcados tambien falla a proposito. Significa que esta base
-- no tiene un usuario 'admin', y dejarla pasar la dejaria sin superadmin: con
-- las reglas de SEG-H09 activas, nadie podria volver a asignar ni quitar el
-- rol ADMINISTRADOR, ni dar de baja a un administrador. Es mejor que el
-- arranque lo diga que descubrirlo al intentar la primera operacion.

DO $$
DECLARE
    v_marcados      integer;
    v_es_admin      integer;
    v_nombre        text;
BEGIN
    SELECT count(*) INTO v_marcados
    FROM usuario
    WHERE es_superadmin;

    SELECT count(*) INTO v_es_admin
    FROM usuario
    WHERE es_superadmin
      AND lower(nombre_usuario) = 'admin';

    IF v_marcados = 0 THEN
        RAISE EXCEPTION
            'V19: ningun usuario quedo marcado como superadmin. Esta base no tiene '
            'un usuario con nombre_usuario = ''admin'' (la semilla de V3); marque a '
            'mano al que corresponda antes de volver a aplicar la migracion';
    END IF;

    IF v_marcados <> 1 THEN
        RAISE EXCEPTION
            'V19: quedaron % usuarios marcados como superadmin y debe haber exactamente uno',
            v_marcados;
    END IF;

    IF v_es_admin <> 1 THEN
        SELECT nombre_usuario INTO v_nombre FROM usuario WHERE es_superadmin;
        RAISE EXCEPTION
            'V19: el superadmin quedo en el usuario ''%'' y debia quedar en ''admin''',
            v_nombre;
    END IF;

    RAISE NOTICE
        'V19: usuario.es_superadmin creada y marcada en el usuario ''admin'' (1 fila en TRUE)';
END $$;
