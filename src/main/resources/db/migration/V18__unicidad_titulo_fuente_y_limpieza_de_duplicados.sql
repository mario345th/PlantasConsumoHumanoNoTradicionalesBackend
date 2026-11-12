-- V18__unicidad_titulo_fuente_y_limpieza_de_duplicados.sql
--
-- Cierra el hallazgo de QA "Fuente título duplicado": la tabla fuente no tenía
-- ninguna restricción de unicidad sobre el título, así que lo único que
-- impedía los duplicados era la comprobación de FuenteServiceImpl, y esa
-- comprobación consulta antes de insertar: entre las dos sentencias cabe otra
-- transacción con el mismo título, y una inserción por SQL directo se la salta
-- por completo. Esta migración limpia los duplicados que hubiera y crea el
-- índice único que faltaba.
--
--
-- POR QUÉ ES V18 Y NO V17
-- =======================
-- La tarea pedía "una migración nueva V17". Ese número ya está tomado Y
-- APLICADO: flyway_schema_history tiene la versión 17
-- (V17__correcciones_moringa_videos_bebida_y_fecha_observacion, installed_rank
-- 15, success = true), igual que la 16. Reusar el número habría hecho que
-- Flyway fallara al arrancar por una versión repetida, y editar ese archivo
-- habría roto su checksum. Así que, por la misma regla que siguieron V12 y
-- V17, la corrección va en el siguiente número libre y no se toca nada
-- aplicado.
--
--
-- LO QUE HACE
-- ===========
--
-- 1. Normaliza los títulos ya guardados (bloque 2): quita los espacios de los
--    extremos y colapsa los internos, exactamente lo que hace
--    FuenteServiceImpl.normalizar() antes de guardar. Hace falta para que la
--    comprobación de la aplicación --que compara el título completo, con
--    existsByTituloIgnoreCase-- y el índice nuevo --que compara
--    lower(btrim(titulo))-- vean siempre lo mismo. Si quedara una fila vieja
--    con espacios alrededor, el índice vería un duplicado donde la consulta no
--    veía ninguno y la aplicación devolvería un 409 sin saber explicarlo.
--
--    Sobre los datos de hoy no cambia nada: los 9 títulos de la base de
--    desarrollo ya están limpios. Se ejecuta igual porque la base de QA y las
--    de cada quien no tienen por qué estarlo.
--
-- 2. Fusiona los duplicados que hubiera (bloque 3), dejando UNA fila por
--    grupo. Sin esto, el CREATE UNIQUE INDEX del bloque 4 falla y la
--    aplicación no arranca para nadie.
--
-- 3. Crea uk_fuente_titulo_lower (bloque 4), índice único funcional sobre
--    lower(btrim(titulo)).
--
--
-- LAS DECISIONES, UNA POR UNA
-- ===========================
--
-- a. Insensible a mayúsculas, como se propuso. Es lo coherente con el resto
--    del esquema --especie.nombre_cientifico, usuario.correo y
--    usuario.nombre_usuario ya tienen índices únicos sobre lower(...)-- y con
--    la comprobación que la aplicación ya hacía, que es IgnoreCase. "USDA
--    FoodData Central" y "usda fooddata central" son la misma fuente escrita
--    de dos formas.
--
-- b. Con btrim dentro del índice, no solo con los datos normalizados por el
--    bloque 2. El bloque 2 arregla lo que ya está guardado; el btrim del
--    índice protege de lo que entre después por SQL directo, que es
--    precisamente el camino que la comprobación de la aplicación no cubre.
--
-- c. NO lleva unaccent. "Guía" y "Guia" siguen siendo dos títulos distintos
--    para el índice. Es la misma limitación que ya documenta
--    Textos.claveComparacion: un índice funcional sobre unaccent(lower(...))
--    obligaría a tener la extensión unaccent instalada en todos los entornos,
--    y eso es una decisión del equipo, no de esta corrección.
--
-- d. NO es parcial. Podría haberse limitado a WHERE activa, para que una
--    fuente dada de baja no estorbara al volver a registrar su título, pero la
--    comprobación de la aplicación tampoco filtra por activa: si el índice
--    fuera parcial, la aplicación seguiría rechazando con 409 un título que la
--    base ya admitiría. Las dos reglas tienen que decir lo mismo. Para
--    reutilizar el título de una fuente dada de baja, se la reactiva
--    (PATCH /api/fuentes/{id}/activar).
--
-- e. Idempotente. El índice se crea con IF NOT EXISTS y la fusión no encuentra
--    nada que fusionar la segunda vez. Flyway no reejecuta una migración
--    aplicada, pero el criterio de V16 y V17 es que correr el script dos veces
--    no rompa nada.
--
--
-- LO QUE NO HACE: TAXONOMÍA
-- =========================
-- La tarea pedía además un UNIQUE NULLS NOT DISTINCT en taxonomía sobre las
-- ocho columnas (reino, division, clase, orden_taxonomico, familia, genero,
-- especie_taxonomica, subespecie). Verificado contra la base real, no procede,
-- y se decidió dejar la restricción como está:
--
--   uk_taxonomia UNIQUE NULLS NOT DISTINCT
--                (reino, genero, especie_taxonomica, subespecie)
--
--   (pg_constraint / pg_indexes, base florav1: CREATE UNIQUE INDEX uk_taxonomia
--    ON public.taxonomia USING btree (reino, genero, especie_taxonomica,
--    subespecie) NULLS NOT DISTINCT)
--
-- Tres razones:
--
--   - Ya cubre el caso de subespecie NULL, que es lo que había que comprobar:
--     NULLS NOT DISTINCT significa que dos NULL cuentan como iguales. Probado
--     en la base de desarrollo: insertar dos veces (Plantae, Zztestus,
--     concurrencia, NULL) falla con "llave duplicada viola restricción de
--     unicidad «uk_taxonomia»", detalle "(reino, genero, especie_taxonomica,
--     subespecie)=(Plantae, Zztestus, concurrencia, null)". Las dos pruebas se
--     hicieron dentro de transacciones revertidas; no quedó ninguna fila.
--
--   - La de ocho columnas sería inútil: si esas cuatro columnas ya son únicas,
--     cualquier tupla que las contenga lo es por construcción. El índice no
--     podría rechazar nunca una fila que la actual acepte; solo ocuparía
--     espacio y habría que mantenerlo.
--
--   - Y sustituir una por otra AFLOJARÍA la regla: pasarían a ser válidas dos
--     especies con el mismo reino, género, especie taxonómica y subespecie que
--     difirieran solo en, por ejemplo, la familia. Comprobado también: con la
--     restricción actual esa fila se rechaza.
--
-- Lo que sí faltaba del hallazgo de taxonomía era en código, y ahí se
-- arregló:
--
--   - EspecieServiceImpl ahora recorta los ocho campos de texto de la
--     clasificación antes de validar y guardar. El reino " Plantae " pasaba la
--     validación del DTO (su @Pattern tolera espacios) y chocaba contra el
--     CHECK ck_taxonomia_reino, que compara lower(reino) = 'plantae' exacto:
--     eso era un 500. Además, al no estar recortado, el valor con espacios no
--     coincidía con ninguna clasificación existente y se saltaba la
--     comprobación de duplicados.
--
--   - La carrera entre la comprobación y el INSERT ya no sale como un 500 ni
--     como el 409 genérico: se atrapa la violación de uk_taxonomia y se
--     responde el mismo 409 explicado que da la comprobación previa, con la
--     especie sin crear a medias (todo ocurre en una transacción).
--     Reproducida la carrera con dos sesiones en paralelo: la segunda se queda
--     esperando en el índice uk_taxonomia --"mientras se insertaba la tupla de
--     índice en la relación «uk_taxonomia»"-- y al confirmar la primera falla
--     con llave duplicada. Es decir que la restricción serializa de verdad las
--     inserciones simultáneas: por ahí no se cuela ningún duplicado.
--
--
-- Verificado del esquema antes de escribir esto
-- =============================================
--   fuente: id_fuente (identity), titulo varchar(300) NOT NULL, autor, anio,
--           tipo_fuente NOT NULL, url, referencia_bibliografica, activa NOT
--           NULL DEFAULT true. Restricciones: fuente_pkey, ck_fuente_anio,
--           ck_fuente_tipo. Índices: solo fuente_pkey -> NO había ninguna
--           unicidad de título.
--   La referencian tres tablas por id_fuente: especie_fuente (PK compuesta
--   (id_especie, id_fuente)), aporte_nutricional (PK id_aporte) y
--   parte_beneficio (PK (id_especie_parte, id_beneficio)). De las tres, solo
--   especie_fuente lleva id_fuente en su clave primaria, y por eso es la única
--   donde repuntar la referencia puede chocar: ver el bloque 3.


-- =====================================================================
-- 1. Foto del estado anterior
-- =====================================================================
-- La tabla completa, para que el bloque 5 pueda demostrar que lo único que
-- cambió fueron los títulos que tenían espacios de más y las filas duplicadas
-- que se fusionaron.

CREATE TEMP TABLE tmp_v18_fuente_antes AS SELECT * FROM fuente;

CREATE TEMP TABLE tmp_v18_hijos_antes AS
SELECT 'especie_fuente'     AS tabla, count(*) AS filas FROM especie_fuente
UNION ALL
SELECT 'aporte_nutricional' AS tabla, count(*) AS filas FROM aporte_nutricional WHERE id_fuente IS NOT NULL
UNION ALL
SELECT 'parte_beneficio'    AS tabla, count(*) AS filas FROM parte_beneficio    WHERE id_fuente IS NOT NULL;


-- =====================================================================
-- 2. Normalización de los títulos ya guardados
-- =====================================================================
-- btrim quita los espacios de los extremos; el regexp_replace colapsa
-- cualquier racha de espacios interna a uno solo. Es lo mismo que hace
-- FuenteServiceImpl.limpiarEspacios() con "\s+" -> " ".
--
-- El WHERE evita reescribir las filas que ya están bien: sin él, el UPDATE
-- tocaría las 9 filas y fecha-actualizaría una tabla entera para nada.

UPDATE fuente
SET titulo = regexp_replace(btrim(titulo), '\s+', ' ', 'g')
WHERE titulo <> regexp_replace(btrim(titulo), '\s+', ' ', 'g');


-- =====================================================================
-- 3. Fusión de los duplicados
-- =====================================================================
-- Se queda la fila de id_fuente más bajo de cada grupo, que es la más
-- antigua: si QA registró el duplicado después, lo que sobrevive es el
-- registro original, con sus referencias ya puestas. Es además un criterio
-- determinista, que importa porque las referencias de las demás filas se
-- mueven a la que se queda.
--
-- OJO: los campos de las filas que se van (autor, anio, url,
-- referencia_bibliografica) se descartan. No se intenta completar la fila que
-- se queda con ellos: eso sería inventar qué versión del dato es la buena, y
-- para dos registros del mismo título capturados dos veces la respuesta no
-- está en los datos. Si al revisar las fuentes faltara un autor o una url, se
-- corrige a mano por el endpoint de actualización.

CREATE TEMP TABLE tmp_v18_fusion AS
SELECT f.id_fuente AS id_sobra,
       g.id_queda
FROM fuente f
JOIN (
    SELECT lower(btrim(titulo)) AS clave,
           min(id_fuente)       AS id_queda
    FROM fuente
    GROUP BY lower(btrim(titulo))
    HAVING count(*) > 1
) g ON g.clave = lower(btrim(f.titulo))
WHERE f.id_fuente <> g.id_queda;

-- 3.1 especie_fuente, la única con id_fuente en la clave primaria.
--
-- Si la misma especie citaba las dos fuentes duplicadas, repuntar la segunda
-- chocaría con especie_fuente_pkey (id_especie, id_fuente). Esa fila se borra
-- en vez de moverse: la relación "esta especie cita esta fuente" ya existe por
-- la fila que se queda, así que no se pierde información de la relación. Lo
-- que sí se pierde es su columna observacion, y queda dicho aquí.
--
-- Las colisiones se listan antes de borrarlas para que la verificación 5.4
-- pueda exigir que especie_fuente perdiera EXACTAMENTE esas filas y ninguna
-- más.
CREATE TEMP TABLE tmp_v18_ef_colisiones AS
SELECT ef.id_especie, ef.id_fuente
FROM especie_fuente ef
JOIN tmp_v18_fusion m ON m.id_sobra = ef.id_fuente
WHERE EXISTS (
    SELECT 1
    FROM especie_fuente ya
    WHERE ya.id_especie = ef.id_especie
      AND ya.id_fuente = m.id_queda
);

DELETE FROM especie_fuente ef
USING tmp_v18_ef_colisiones c
WHERE ef.id_especie = c.id_especie
  AND ef.id_fuente = c.id_fuente;

UPDATE especie_fuente ef
SET id_fuente = m.id_queda
FROM tmp_v18_fusion m
WHERE ef.id_fuente = m.id_sobra;

-- 3.2 Las otras dos. Sus claves (id_aporte; id_especie_parte + id_beneficio)
-- no incluyen id_fuente, así que mover la referencia no puede chocar con
-- nada: es un simple UPDATE.
UPDATE aporte_nutricional a
SET id_fuente = m.id_queda
FROM tmp_v18_fusion m
WHERE a.id_fuente = m.id_sobra;

UPDATE parte_beneficio p
SET id_fuente = m.id_queda
FROM tmp_v18_fusion m
WHERE p.id_fuente = m.id_sobra;

-- 3.3 Y ya sin referencias apuntándolas, las filas duplicadas.
-- Es un DELETE físico y no una baja lógica a propósito: no son contenido que
-- fue válido y se retira, son el mismo registro escrito dos veces. Mismo
-- criterio que V12 con los roles que sobraban y que V17 con los videos que
-- estaban en el nivel equivocado.
DELETE FROM fuente f
USING tmp_v18_fusion m
WHERE f.id_fuente = m.id_sobra;


-- =====================================================================
-- 4. El índice único que faltaba
-- =====================================================================

CREATE UNIQUE INDEX IF NOT EXISTS uk_fuente_titulo_lower
    ON fuente (lower(btrim(titulo)));

COMMENT ON INDEX uk_fuente_titulo_lower IS
    'Un título de fuente no se repite, sin distinguir mayúsculas ni espacios '
    'de los extremos. FuenteServiceImpl se anticipa a este índice para '
    'devolver un 409 explicado, y atrapa su violación por si la comprobación '
    'previa pierde la carrera contra otra transacción.';


-- =====================================================================
-- 5. Verificación
-- =====================================================================
-- Si algo de lo anterior no quedó como dice el encabezado, la migración falla
-- aquí y Flyway revierte la transacción completa: PostgreSQL hace DDL
-- transaccional, así que el índice tampoco se queda a medias.

DO $$
DECLARE
    v_grupos_repetidos integer;
    v_sin_normalizar   integer;
    v_fusionadas       integer;
    v_indice           integer;
    v_huerfanos        integer;
    v_normalizados     integer;
BEGIN
    -- 5.1 No queda ningún título repetido.
    SELECT count(*) INTO v_grupos_repetidos
    FROM (
        SELECT lower(btrim(titulo))
        FROM fuente
        GROUP BY lower(btrim(titulo))
        HAVING count(*) > 1
    ) g;

    IF v_grupos_repetidos <> 0 THEN
        RAISE EXCEPTION
            'V18: quedan % titulos de fuente repetidos despues de la limpieza', v_grupos_repetidos;
    END IF;

    -- 5.2 Ningún título guardado tiene espacios de sobra, de modo que la
    -- comprobación de la aplicación y el índice miran el mismo valor.
    SELECT count(*) INTO v_sin_normalizar
    FROM fuente
    WHERE titulo <> regexp_replace(btrim(titulo), '\s+', ' ', 'g');

    IF v_sin_normalizar <> 0 THEN
        RAISE EXCEPTION
            'V18: % titulos de fuente siguen con espacios sin normalizar', v_sin_normalizar;
    END IF;

    -- 5.3 El índice existe, es único y es el funcional que se pidió.
    SELECT count(*) INTO v_indice
    FROM pg_indexes
    WHERE schemaname = current_schema()
      AND tablename = 'fuente'
      AND indexname = 'uk_fuente_titulo_lower'
      AND indexdef LIKE '%UNIQUE%'
      AND indexdef LIKE '%lower(btrim%';

    IF v_indice <> 1 THEN
        RAISE EXCEPTION
            'V18: uk_fuente_titulo_lower no quedo creado como indice unico funcional';
    END IF;

    -- 5.4 Desaparecieron exactamente las fuentes duplicadas, ni una más.
    -- Que no quede ninguna referencia apuntando a una fuente borrada ya lo
    -- garantizan las tres FK: si el bloque 3 se hubiera dejado una, el DELETE
    -- del 3.3 habría fallado y la migración no se aplicaría. Lo que se
    -- comprueba aquí es lo contrario, que no se borró de más.
    SELECT count(*) INTO v_fusionadas FROM tmp_v18_fusion;

    SELECT count(*) INTO v_huerfanos
    FROM tmp_v18_fuente_antes a
    WHERE NOT EXISTS (SELECT 1 FROM fuente f WHERE f.id_fuente = a.id_fuente);

    IF v_huerfanos <> v_fusionadas THEN
        RAISE EXCEPTION
            'V18: se esperaban % fuentes eliminadas (las duplicadas) y desaparecieron %',
            v_fusionadas, v_huerfanos;
    END IF;

    -- 5.5 Las tres tablas hijas conservan sus filas, salvo las de
    -- especie_fuente que se borraron por colisionar con la fila que se queda
    -- (bloque 3.1). Una referencia que se moviera a la fuente equivocada no la
    -- caza esta cuenta, pero una que se perdiera sí, y perderla es el riesgo
    -- real de una fusión.
    PERFORM 1
    FROM tmp_v18_hijos_antes antes
    WHERE antes.filas <> CASE antes.tabla
            WHEN 'especie_fuente' THEN
                (SELECT count(*) FROM especie_fuente)
                + (SELECT count(*) FROM tmp_v18_ef_colisiones)
            WHEN 'aporte_nutricional' THEN
                (SELECT count(*) FROM aporte_nutricional WHERE id_fuente IS NOT NULL)
            WHEN 'parte_beneficio' THEN
                (SELECT count(*) FROM parte_beneficio WHERE id_fuente IS NOT NULL)
          END;

    IF FOUND THEN
        RAISE EXCEPTION
            'V18: alguna tabla que referencia fuente perdio filas que no eran colisiones de especie_fuente';
    END IF;

    -- Cuantos titulos reescribio el bloque 2: los que sobrevivieron y cuyo
    -- texto ya no es el de la foto.
    SELECT count(*) INTO v_normalizados
    FROM tmp_v18_fuente_antes a
    JOIN fuente f ON f.id_fuente = a.id_fuente
    WHERE f.titulo <> a.titulo;

    RAISE NOTICE
        'V18: % fuentes duplicadas fusionadas, % titulos normalizados, uk_fuente_titulo_lower creado',
        v_fusionadas, v_normalizados;
END $$;
