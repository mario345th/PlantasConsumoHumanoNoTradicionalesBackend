-- V11__indice_parcial_ubicacion_especie.sql
-- Permite volver a registrar una ubicacion que fue dada de baja.
-- Responde al hallazgo AN-H12 del 3er reporte de QA Testing: el UNIQUE
-- (id_especie, latitud, longitud) cuenta tambien las filas desactivadas, asi
-- que tras la baja logica el punto quedaba bloqueado para siempre y no habia
-- forma de volver a registrar ese avistamiento.
--
-- Se reemplaza por un indice unico PARCIAL, con el mismo criterio que
-- uk_imagen_principal_preparacion y los indices de video de V10: la unicidad
-- solo aplica entre las filas activas, y el historial desactivado no estorba.
-- El servicio comprueba lo mismo con existsBy...ActivaTrue y devuelve un 409
-- con mensaje propio antes del INSERT (AN-H06), de modo que la secuencia ya no
-- se consume en los intentos rechazados; este indice es la red de seguridad.
--
-- La tabla ubicacion_especie se creo antes de que el proyecto usara Flyway
-- (baseline-version=3), por lo que el nombre de su restriccion UNIQUE no esta
-- en ninguna migracion y puede diferir entre entornos. Por eso se busca por su
-- definicion (las columnas que cubre) y no por nombre.

-- 1. Quitar la restriccion UNIQUE declarada en la tabla, si existe.
DO
$$
    DECLARE
        nombre_restriccion text;
    BEGIN
        SELECT con.conname
        INTO nombre_restriccion
        FROM pg_constraint con
                 JOIN pg_class rel ON rel.oid = con.conrelid
                 JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE nsp.nspname = current_schema()
          AND rel.relname = 'ubicacion_especie'
          AND con.contype = 'u'
          -- attname es de tipo "name": se convierte a text porque no existe
          -- el operador name[] = text[].
          AND (SELECT array_agg(att.attname::text ORDER BY att.attname::text)
               FROM unnest(con.conkey) AS k(attnum)
                        JOIN pg_attribute att
                             ON att.attrelid = con.conrelid AND att.attnum = k.attnum)
            = ARRAY ['id_especie', 'latitud', 'longitud']
        LIMIT 1;

        IF nombre_restriccion IS NULL THEN
            RAISE NOTICE 'ubicacion_especie: sin UNIQUE declarado sobre (id_especie, latitud, longitud).';
        ELSE
            EXECUTE format('ALTER TABLE ubicacion_especie DROP CONSTRAINT %I', nombre_restriccion);
            RAISE NOTICE 'ubicacion_especie: eliminada la restriccion %', nombre_restriccion;
        END IF;
    END
$$;

-- 2. Quitar un indice unico equivalente que no venga de una restriccion.
--    Cubre el entorno donde se creo con CREATE UNIQUE INDEX en lugar de
--    declararse en la tabla; el indice nuevo se llama distinto, asi que no se
--    borra a si mismo.
DO
$$
    DECLARE
        nombre_indice text;
    BEGIN
        SELECT cls.relname
        INTO nombre_indice
        FROM pg_index idx
                 JOIN pg_class cls ON cls.oid = idx.indexrelid
                 JOIN pg_class tbl ON tbl.oid = idx.indrelid
                 JOIN pg_namespace nsp ON nsp.oid = tbl.relnamespace
        WHERE nsp.nspname = current_schema()
          AND tbl.relname = 'ubicacion_especie'
          AND idx.indisunique
          AND idx.indpred IS NULL
          AND NOT EXISTS (SELECT 1 FROM pg_constraint con WHERE con.conindid = idx.indexrelid)
          -- indkey es un int2vector: hay que convertirlo para poder recorrerlo.
          AND (SELECT array_agg(att.attname::text ORDER BY att.attname::text)
               FROM unnest(idx.indkey::int2[]) AS k(attnum)
                        JOIN pg_attribute att
                             ON att.attrelid = idx.indrelid AND att.attnum = k.attnum)
            = ARRAY ['id_especie', 'latitud', 'longitud']
        LIMIT 1;

        IF nombre_indice IS NOT NULL THEN
            EXECUTE format('DROP INDEX %I', nombre_indice);
            RAISE NOTICE 'ubicacion_especie: eliminado el indice %', nombre_indice;
        END IF;
    END
$$;

-- 3. Dar de baja los duplicados que ya esten activos, porque si no el
--    CREATE UNIQUE INDEX falla y, con Flyway en el arranque, la aplicacion no
--    levanta para nadie. No se borra ninguna fila: se conserva la de id mas
--    bajo de cada grupo, que es la primera registrada.
UPDATE ubicacion_especie u
SET activa = false
WHERE u.activa = true
  AND EXISTS (SELECT 1
              FROM ubicacion_especie previa
              WHERE previa.activa = true
                AND previa.id_especie = u.id_especie
                AND previa.latitud = u.latitud
                AND previa.longitud = u.longitud
                AND previa.id_ubicacion < u.id_ubicacion);

-- 4. La unicidad, ahora solo entre las ubicaciones activas.
CREATE UNIQUE INDEX uk_ubicacion_especie_coords_activa
    ON ubicacion_especie (id_especie, latitud, longitud)
    WHERE activa = true;
