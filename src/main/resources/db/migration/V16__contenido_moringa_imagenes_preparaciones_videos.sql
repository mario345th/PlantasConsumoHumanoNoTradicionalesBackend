-- V16__contenido_moringa_imagenes_preparaciones_videos.sql
--
-- Agrega contenido a UNA sola especie, Moringa (nombre_cientifico
-- 'Moringa oleifera'): 3 imágenes generales más, las 7 formas de preparación
-- de sus tres partes comestibles (Hoja, Flor, Semilla) y 4 videos a nivel de
-- especie.
--
-- ALCANCE: solo Moringa. Las otras 14 especies que cargó V14 no se tocan, y
-- eso no se afirma de palabra: el bloque 2 guarda una foto completa de todas
-- las filas de imagen_especie, video_especie y preparacion_consumo que NO son
-- de Moringa, y la aserción 6.5 la compara fila por fila y columna por columna
-- después de los INSERT. Tampoco se toca ningún catálogo (especie,
-- especie_parte_comestible, parte_planta): esta migración solo inserta en las
-- tres tablas de contenido.
--
--
-- Decisiones
-- ==========
--
-- 1. Clave natural y una sola resolución. Ninguna PK se escribe literal: las
--    tres tablas tienen id GENERATED ALWAYS AS IDENTITY. La especie se
--    encuentra por lower(nombre_cientifico) (uk_especie_nombre_cientifico_lower)
--    y sus partes por parte_planta.nombre (parte_planta_nombre_key). Esa
--    resolución ocurre UNA vez, en el bloque 1 (tmp_v16_moringa y
--    tmp_v16_partes), y de ahí la leen los tres INSERT y la verificación; así
--    no hay forma de que una sentencia resuelva la especie de una manera y
--    otra de otra, igual que el criterio de V15.
--
-- 2. Idempotencia. Los tres INSERT llevan NOT EXISTS, así que correr el script
--    dos veces no duplica nada:
--      imagen_especie      -> por (id_especie, url_imagen). La tabla NO tiene
--                             índice único de URL (el único índice único es
--                             uk_imagen_principal_especie), así que este
--                             NOT EXISTS es la única defensa.
--      video_especie       -> por (id_especie, url_video), que además coincide
--                             con el índice parcial único
--                             uk_video_especie_url_activa (WHERE activo) que
--                             creó V10.
--      preparacion_consumo -> por (id_especie_parte, nombre), que es
--                             exactamente la constraint única
--                             uk_preparacion_parte_nombre.
--
-- 3. Valores de enum tomados de los CHECK de la base real y confirmados contra
--    las clases de entity/enums (se persisten como texto):
--      imagen_especie.tipo_imagen (ck_imagen_tipo) -> PLANTA_COMPLETA, HOJA,
--        FLOR, FRUTO, SEMILLA, RAIZ, TALLO, CORTEZA, PARTE_COMESTIBLE, OTRA
--      preparacion_consumo.tipo_consumo (ck_preparacion_tipo) -> CRUDO,
--        COCIDO, ASADO, FRITO, BEBIDA, INFUSION, OTRO
--
--    Las tres imágenes son fotografías generales de la planta, con
--    id_especie_parte NULL, así que usan PLANTA_COMPLETA, el mismo valor que
--    eligió V14 para ese caso (el enum no tiene ningún valor "GENERAL").
--
--    Mapeo de tipo_consumo pedido, resuelto contra el enum real:
--      infusión -> INFUSION  (existe; no hubo que caer a COCIDO)
--      tortitas -> FRITO     (existe)
--      tostado  -> ASADO     (existe; no hubo que caer a COCIDO)
--    Las demás quedan en COCIDO, que es lo que traía la tabla de origen.
--
-- 4. es_principal = FALSE en las tres imágenes nuevas. Moringa ya trae de V14
--    su imagen principal, y uk_imagen_principal_especie es UNIQUE (id_especie)
--    WHERE es_principal AND activa: una segunda principal activa haría fallar
--    la migración. La aserción 6.2 comprueba que la principal sigue siendo la
--    de V14 y que es una sola.
--
-- 5. Columnas NOT NULL que la tabla de origen no traía:
--      imagen_especie.texto_alternativo (varchar(300) NOT NULL) -> el texto
--      que pidió la tarea, igual para las tres.
--    Lo que sí admite NULL se deja en NULL y no se inventa relleno
--    (verificado contra la base): imagen titulo/descripcion/creditos,
--    preparación ingredientes/tiempo_preparacion_min/numero_porciones y las
--    advertencias que la tabla de origen traía vacías, video
--    duracion_segundos/creditos.
--
-- 6. fecha_registro no se escribe en ninguna de las dos tablas que la tienen.
--    Es NOT NULL con DEFAULT CURRENT_TIMESTAMP y las entidades la marcan
--    @Generated(INSERT) y updatable = false: la pone la base.
--
-- 7. LIMITACIÓN REAL del esquema, no un descuido de este script:
--    video_especie NO tiene columna de orden ni de tipo/plataforma. Sus
--    columnas reales son id_video, id_especie, id_especie_parte, titulo,
--    descripcion, url_video, duracion_segundos, creditos, activo y
--    fecha_registro (verificado con \d contra la base, no solo contra la
--    entidad VideoEspecie). Por lo tanto:
--      - El "orden" 1..4 de los cuatro videos no se puede guardar. Lo único
--        que se puede hacer sin cambiar el esquema es insertarlos EN ese orden
--        (el INSERT lleva ORDER BY d.orden), de modo que id_video quede
--        ascendente en el orden pedido. Si el orden debe ser un dato de
--        verdad, hace falta otra migración que agregue la columna y su mapeo
--        en la entidad; esta no inventa dónde guardarlo.
--      - No hay enum de YouTube/externo al que mapear nada: la plataforma la
--        lleva la propia url_video. Tampoco se sube ningún archivo.
--    preparacion_consumo sí tiene orden_presentacion (smallint NOT NULL
--    DEFAULT 1, con ck_preparacion_orden > 0), y ahí sí viaja el orden pedido.
--    La columna de baja lógica de video_especie es "activo" (masculino); en
--    imagen_especie y preparacion_consumo es "activa".
--
-- 8. Si cualquier aserción del bloque 6 falla, el RAISE EXCEPTION hace que
--    Flyway revierta la migración completa. Esa red es necesaria porque los
--    tres INSERT resuelven sus FK con JOIN, y un JOIN que no encuentra su fila
--    no falla: simplemente no inserta. Sin el bloque 6, un nombre de parte mal
--    escrito dejaría a Moringa cargada a medias y la migración marcada como
--    exitosa.


-- =====================================================================
-- 1. Resolución de la especie y de sus partes
-- =====================================================================
-- Único punto donde se traduce clave natural -> id. Todo lo demás lee de aquí.

CREATE TEMP TABLE tmp_v16_moringa (
    id_especie bigint PRIMARY KEY
);

INSERT INTO tmp_v16_moringa (id_especie)
SELECT e.id_especie
FROM especie e
WHERE lower(e.nombre_cientifico) = lower('Moringa oleifera');

CREATE TEMP TABLE tmp_v16_partes (
    parte            varchar(80) PRIMARY KEY,
    id_especie_parte bigint NOT NULL UNIQUE
);

INSERT INTO tmp_v16_partes (parte, id_especie_parte)
SELECT pp.nombre, epc.id_especie_parte
FROM especie_parte_comestible epc
JOIN tmp_v16_moringa m ON m.id_especie = epc.id_especie
JOIN parte_planta pp   ON pp.id_parte_planta = epc.id_parte_planta
WHERE pp.nombre IN ('Hoja', 'Flor', 'Semilla');

-- Conviene fallar aquí y no después: con la especie o las partes sin resolver,
-- la foto del bloque 2 se armaría sobre el conjunto equivocado y el mensaje de
-- error sería mucho menos claro.
DO $$
DECLARE
    v_especies integer;
    v_partes   integer;
BEGIN
    SELECT count(*) INTO v_especies FROM tmp_v16_moringa;
    IF v_especies <> 1 THEN
        RAISE EXCEPTION
            'V16: se esperaba 1 especie con nombre_cientifico Moringa oleifera y hay %', v_especies;
    END IF;

    SELECT count(*) INTO v_partes FROM tmp_v16_partes;
    IF v_partes <> 3 THEN
        RAISE EXCEPTION
            'V16: se esperaban 3 partes comestibles de Moringa (Hoja, Flor, Semilla) y se resolvieron %', v_partes;
    END IF;
END $$;


-- =====================================================================
-- 2. Foto del estado anterior de las demás especies
-- =====================================================================
-- Se guarda TODO lo que no debe cambiar, con todas sus columnas, para poder
-- demostrarlo en la aserción 6.5. SELECT * a propósito: así la comparación
-- cubre también cualquier columna que alguien agregue más adelante.

CREATE TEMP TABLE tmp_v16_otras_imagenes AS
SELECT * FROM imagen_especie
WHERE id_especie NOT IN (SELECT id_especie FROM tmp_v16_moringa);

CREATE TEMP TABLE tmp_v16_otros_videos AS
SELECT * FROM video_especie
WHERE id_especie NOT IN (SELECT id_especie FROM tmp_v16_moringa);

CREATE TEMP TABLE tmp_v16_otras_preparaciones AS
SELECT * FROM preparacion_consumo
WHERE id_especie_parte NOT IN (SELECT id_especie_parte FROM tmp_v16_partes);


-- =====================================================================
-- 3. Imágenes nuevas (3, generales de la especie)
-- =====================================================================

CREATE TEMP TABLE tmp_v16_imagenes (
    url_imagen varchar(1000) PRIMARY KEY
);

INSERT INTO tmp_v16_imagenes (url_imagen)
VALUES
    ('https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTnYvWUt-AoeySTZ-KB9jFj57B5vTz4QGML9CgjZFXSqJxJRMC8WhqSiaTa&s=10'),
    ('https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRRf3t95yrEWf4DvHjMXaQXGsMfLO52k0G-SmloD1609DizJuLut1gPDnBP&s=10'),
    ('https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSp-u5588yGptjkebZmH9YQztXuqgcseApiuXdVgbGiUMQH9pzEE352h2s&s=10');

INSERT INTO imagen_especie (id_especie, id_especie_parte, tipo_imagen, url_imagen,
                            titulo, descripcion, texto_alternativo, creditos,
                            es_principal, activa)
SELECT m.id_especie,
       NULL,
       'PLANTA_COMPLETA',
       d.url_imagen,
       NULL,
       NULL,
       'Fotografía de la planta de Moringa (Moringa oleifera).',
       NULL,
       false,
       true
FROM tmp_v16_imagenes d
CROSS JOIN tmp_v16_moringa m
WHERE NOT EXISTS (
    SELECT 1 FROM imagen_especie i
    WHERE i.id_especie = m.id_especie
      AND i.url_imagen = d.url_imagen
);


-- =====================================================================
-- 4. Formas de preparación (7, repartidas en las tres partes)
-- =====================================================================
-- requiere_coccion = TRUE, activa = TRUE y fuente_tradicional son iguales en
-- las siete: van fijas en el SELECT y no se repiten por fila. advertencias sí
-- viaja en la lista porque solo dos preparaciones la traen; las otras cinco
-- quedan en NULL (la columna lo admite).

CREATE TEMP TABLE tmp_v16_preparaciones (
    parte         varchar(80)  NOT NULL,
    nombre        varchar(150) NOT NULL,
    tipo_consumo  varchar(15)  NOT NULL,
    descripcion   text         NOT NULL,
    procedimiento text         NOT NULL,
    advertencias  text,
    orden         smallint     NOT NULL,
    PRIMARY KEY (parte, nombre)
);

INSERT INTO tmp_v16_preparaciones (parte, nombre, tipo_consumo, descripcion,
                                   procedimiento, advertencias, orden)
VALUES
    ('Hoja', 'Hojas en sopa o caldo', 'COCIDO',
     'Hojas tiernas añadidas a sopas, caldos o guisos al final de la cocción.',
     'Lavar las hojas, separarlas del tallo y agregarlas 3–5 min antes de servir.',
     NULL, 1),

    ('Hoja', 'Infusión de hoja (té)', 'INFUSION',
     'Hojas frescas o secas en infusión con agua caliente.',
     'Verter agua caliente sobre 1 cda de hoja seca (o un puñado de fresca), reposar 5 min y colar.',
     NULL, 2),

    ('Hoja', 'Hojas con huevo', 'COCIDO',
     'Hojas picadas revueltas con huevo o en tortilla.',
     'Picar las hojas y revolverlas con huevo a fuego medio.',
     NULL, 3),

    ('Flor', 'Flores salteadas o en tortitas', 'FRITO',
     'Flores cocidas ligeramente, salteadas o rebozadas en tortitas con huevo.',
     'Lavar las flores; saltearlas con cebolla, o mezclarlas con huevo y freír en tortitas.',
     NULL, 1),

    ('Flor', 'Flores en sopa', 'COCIDO',
     'Flores añadidas a sopas y caldos.',
     'Agregar las flores limpias a la sopa unos minutos antes de servir.',
     NULL, 2),

    ('Semilla', 'Semillas tiernas cocidas', 'COCIDO',
     'Vainas o semillas inmaduras hervidas, consumidas como legumbre.',
     'Hervir las vainas/semillas tiernas en agua con sal hasta ablandar.',
     'Preferir las semillas tiernas; las maduras son fibrosas y amargas.', 1),

    ('Semilla', 'Semillas tostadas', 'ASADO',
     'Semillas maduras tostadas, como snack o molidas.',
     'Tostar las semillas peladas en sartén a fuego medio hasta dorar.',
     'Consumir con moderación.', 2);

INSERT INTO preparacion_consumo (id_especie_parte, nombre, descripcion, tipo_consumo,
                                 requiere_coccion, ingredientes, procedimiento,
                                 tiempo_preparacion_min, numero_porciones, advertencias,
                                 fuente_tradicional, orden_presentacion, activa)
SELECT pt.id_especie_parte,
       d.nombre,
       d.descripcion,
       d.tipo_consumo,
       true,
       NULL,
       d.procedimiento,
       NULL,
       NULL,
       d.advertencias,
       'Uso culinario tradicional',
       d.orden,
       true
FROM tmp_v16_preparaciones d
JOIN tmp_v16_partes pt ON pt.parte = d.parte
WHERE NOT EXISTS (
    SELECT 1 FROM preparacion_consumo p
    WHERE p.id_especie_parte = pt.id_especie_parte
      AND p.nombre = d.nombre
);


-- =====================================================================
-- 5. Videos (4, a nivel de especie)
-- =====================================================================
-- id_especie_parte NULL: son videos de la especie, no de una parte. La FK
-- compuesta fk_video_parte_especie solo actúa cuando esa columna trae valor.
--
-- El ORDER BY d.orden es lo único que puede expresar el orden pedido, porque
-- la tabla no tiene columna de orden (ver nota 7 del encabezado): hace que
-- id_video quede ascendente en el orden de la tabla de origen.

CREATE TEMP TABLE tmp_v16_videos (
    url_video   varchar(1000) PRIMARY KEY,
    titulo      varchar(200)  NOT NULL,
    descripcion text          NOT NULL,
    orden       smallint      NOT NULL
);

INSERT INTO tmp_v16_videos (url_video, titulo, descripcion, orden)
VALUES
    ('https://www.youtube.com/watch?v=szFyNYiqa4s',
     '¿Cómo se come la moringa? Hojas, flores, raíces',
     'Formas de consumir las distintas partes de la moringa.', 1),

    ('https://www.youtube.com/watch?v=JiCwC9iKIAY',
     'Todo sobre la moringa: propiedades, usos y cómo tomarla',
     'Propiedades nutricionales y usos de la moringa.', 2),

    ('https://www.youtube.com/watch?v=E_q-l-peipU',
     'Té de moringa',
     'Preparación de té (infusión) de moringa.', 3),

    ('https://www.youtube.com/watch?v=lSRYa_mPA5k',
     'Bebida de moringa y huevo con moringa',
     'Preparación de una bebida de moringa y de huevo con moringa.', 4);

INSERT INTO video_especie (id_especie, id_especie_parte, titulo, descripcion,
                           url_video, duracion_segundos, creditos, activo)
SELECT m.id_especie,
       NULL,
       d.titulo,
       d.descripcion,
       d.url_video,
       NULL,
       NULL,
       true
FROM tmp_v16_videos d
CROSS JOIN tmp_v16_moringa m
WHERE NOT EXISTS (
    SELECT 1 FROM video_especie v
    WHERE v.id_especie = m.id_especie
      AND v.url_video = d.url_video
)
ORDER BY d.orden;


-- =====================================================================
-- 6. Verificación
-- =====================================================================
-- Red de seguridad. Cualquier fallo levanta RAISE EXCEPTION y Flyway revierte
-- la migración completa, así que no puede quedar media carga aplicada.

DO $$
DECLARE
    v_imagenes        integer;
    v_imagenes_nuevas integer;
    v_mal_formadas    integer;
    v_principales     integer;
    v_preps           integer;
    v_preps_ligadas   integer;
    v_hoja            integer;
    v_flor            integer;
    v_semilla         integer;
    v_videos          integer;
    v_videos_nuevos   integer;
    v_otras_img       integer;
    v_otros_vid       integer;
    v_otras_prep      integer;
BEGIN
    -- 6.1 Moringa quedó con exactamente 4 imágenes (1 de V14 + 3 nuevas) y las
    --     3 URLs nuevas están presentes.
    SELECT count(*) INTO v_imagenes
    FROM imagen_especie i
    JOIN tmp_v16_moringa m ON m.id_especie = i.id_especie;

    IF v_imagenes <> 4 THEN
        RAISE EXCEPTION
            'V16: Moringa deberia tener 4 imagenes (1 previa + 3 nuevas) y tiene %', v_imagenes;
    END IF;

    SELECT count(*) INTO v_imagenes_nuevas
    FROM imagen_especie i
    JOIN tmp_v16_moringa m  ON m.id_especie = i.id_especie
    JOIN tmp_v16_imagenes d ON d.url_imagen = i.url_imagen;

    IF v_imagenes_nuevas <> 3 THEN
        RAISE EXCEPTION
            'V16: se esperaban las 3 URLs de imagen nuevas en Moringa y hay %', v_imagenes_nuevas;
    END IF;

    -- Y quedaron con la forma pedida, no solo con la URL correcta.
    SELECT count(*) INTO v_mal_formadas
    FROM imagen_especie i
    JOIN tmp_v16_moringa m  ON m.id_especie = i.id_especie
    JOIN tmp_v16_imagenes d ON d.url_imagen = i.url_imagen
    WHERE i.id_especie_parte IS NOT NULL
       OR i.tipo_imagen <> 'PLANTA_COMPLETA'
       OR i.texto_alternativo <> 'Fotografía de la planta de Moringa (Moringa oleifera).'
       OR i.es_principal <> false
       OR i.activa <> true;

    IF v_mal_formadas > 0 THEN
        RAISE EXCEPTION
            'V16: % imagen(es) nueva(s) de Moringa no quedaron como generales de la especie (parte NULL, PLANTA_COMPLETA, no principal, activa)',
            v_mal_formadas;
    END IF;

    -- 6.2 La imagen principal de Moringa sigue siendo una sola y es la de V14,
    --     no una de las nuevas (uk_imagen_principal_especie).
    SELECT count(*) INTO v_principales
    FROM imagen_especie i
    JOIN tmp_v16_moringa m ON m.id_especie = i.id_especie
    WHERE i.es_principal
      AND i.activa
      AND i.url_imagen NOT IN (SELECT url_imagen FROM tmp_v16_imagenes);

    IF v_principales <> 1 THEN
        RAISE EXCEPTION
            'V16: Moringa deberia conservar exactamente 1 imagen principal activa de las previas y tiene %', v_principales;
    END IF;

    -- 6.3 Siete preparaciones de Moringa: 3 Hoja, 2 Flor, 2 Semilla, cada una
    --     ligada a la parte que le corresponde y con los valores pedidos.
    SELECT count(*) INTO v_preps
    FROM preparacion_consumo p
    JOIN tmp_v16_partes pt ON pt.id_especie_parte = p.id_especie_parte;

    IF v_preps <> 7 THEN
        RAISE EXCEPTION
            'V16: Moringa deberia tener 7 preparaciones y tiene %', v_preps;
    END IF;

    SELECT count(*) INTO v_preps_ligadas
    FROM tmp_v16_preparaciones d
    JOIN tmp_v16_partes pt     ON pt.parte = d.parte
    JOIN preparacion_consumo p ON p.id_especie_parte = pt.id_especie_parte
                              AND p.nombre = d.nombre
    WHERE p.tipo_consumo       = d.tipo_consumo
      AND p.descripcion        = d.descripcion
      AND p.procedimiento      = d.procedimiento
      AND p.advertencias IS NOT DISTINCT FROM d.advertencias
      AND p.orden_presentacion = d.orden
      AND p.requiere_coccion
      AND p.activa
      AND p.fuente_tradicional = 'Uso culinario tradicional'
      AND p.ingredientes           IS NULL
      AND p.tiempo_preparacion_min IS NULL
      AND p.numero_porciones       IS NULL;

    IF v_preps_ligadas <> 7 THEN
        RAISE EXCEPTION
            'V16: se esperaban las 7 preparaciones ligadas a su parte y con sus valores, y calzan % (revisar los nombres de parte)',
            v_preps_ligadas;
    END IF;

    SELECT count(*) INTO v_hoja
    FROM preparacion_consumo p
    JOIN tmp_v16_partes pt ON pt.id_especie_parte = p.id_especie_parte
    WHERE pt.parte = 'Hoja';

    SELECT count(*) INTO v_flor
    FROM preparacion_consumo p
    JOIN tmp_v16_partes pt ON pt.id_especie_parte = p.id_especie_parte
    WHERE pt.parte = 'Flor';

    SELECT count(*) INTO v_semilla
    FROM preparacion_consumo p
    JOIN tmp_v16_partes pt ON pt.id_especie_parte = p.id_especie_parte
    WHERE pt.parte = 'Semilla';

    IF v_hoja <> 3 OR v_flor <> 2 OR v_semilla <> 2 THEN
        RAISE EXCEPTION
            'V16: el reparto por parte deberia ser Hoja 3 / Flor 2 / Semilla 2 y es Hoja % / Flor % / Semilla %',
            v_hoja, v_flor, v_semilla;
    END IF;

    -- 6.4 Cuatro videos de Moringa, con esas cuatro URLs y a nivel de especie.
    SELECT count(*) INTO v_videos
    FROM video_especie v
    JOIN tmp_v16_moringa m ON m.id_especie = v.id_especie;

    IF v_videos <> 4 THEN
        RAISE EXCEPTION
            'V16: Moringa deberia tener 4 videos y tiene %', v_videos;
    END IF;

    SELECT count(*) INTO v_videos_nuevos
    FROM video_especie v
    JOIN tmp_v16_moringa m ON m.id_especie = v.id_especie
    JOIN tmp_v16_videos d  ON d.url_video = v.url_video
    WHERE v.titulo = d.titulo
      AND v.descripcion = d.descripcion
      AND v.id_especie_parte IS NULL
      AND v.activo;

    IF v_videos_nuevos <> 4 THEN
        RAISE EXCEPTION
            'V16: se esperaban los 4 videos nuevos de Moringa con su titulo y descripcion y hay %', v_videos_nuevos;
    END IF;

    -- 6.5 Ninguna fila de otra especie fue tocada. La diferencia simétrica
    --     contra la foto del bloque 2 vale 0 solo si no cambió ningún valor,
    --     no se perdió ninguna fila y no apareció ninguna nueva.
    SELECT count(*) INTO v_otras_img FROM (
        (SELECT * FROM imagen_especie
          WHERE id_especie NOT IN (SELECT id_especie FROM tmp_v16_moringa)
         EXCEPT ALL
         SELECT * FROM tmp_v16_otras_imagenes)
        UNION ALL
        (SELECT * FROM tmp_v16_otras_imagenes
         EXCEPT ALL
         SELECT * FROM imagen_especie
          WHERE id_especie NOT IN (SELECT id_especie FROM tmp_v16_moringa))
    ) q;

    IF v_otras_img > 0 THEN
        RAISE EXCEPTION
            'V16: % fila(s) de imagen_especie de otras especies cambiaron; esta migracion solo debe agregar contenido a Moringa',
            v_otras_img;
    END IF;

    SELECT count(*) INTO v_otros_vid FROM (
        (SELECT * FROM video_especie
          WHERE id_especie NOT IN (SELECT id_especie FROM tmp_v16_moringa)
         EXCEPT ALL
         SELECT * FROM tmp_v16_otros_videos)
        UNION ALL
        (SELECT * FROM tmp_v16_otros_videos
         EXCEPT ALL
         SELECT * FROM video_especie
          WHERE id_especie NOT IN (SELECT id_especie FROM tmp_v16_moringa))
    ) q;

    IF v_otros_vid > 0 THEN
        RAISE EXCEPTION
            'V16: % fila(s) de video_especie de otras especies cambiaron; esta migracion solo debe agregar contenido a Moringa',
            v_otros_vid;
    END IF;

    SELECT count(*) INTO v_otras_prep FROM (
        (SELECT * FROM preparacion_consumo
          WHERE id_especie_parte NOT IN (SELECT id_especie_parte FROM tmp_v16_partes)
         EXCEPT ALL
         SELECT * FROM tmp_v16_otras_preparaciones)
        UNION ALL
        (SELECT * FROM tmp_v16_otras_preparaciones
         EXCEPT ALL
         SELECT * FROM preparacion_consumo
          WHERE id_especie_parte NOT IN (SELECT id_especie_parte FROM tmp_v16_partes))
    ) q;

    IF v_otras_prep > 0 THEN
        RAISE EXCEPTION
            'V16: % fila(s) de preparacion_consumo de otras especies cambiaron; esta migracion solo debe agregar contenido a Moringa',
            v_otras_prep;
    END IF;
END $$;


-- =====================================================================
-- 7. Limpieza
-- =====================================================================
-- Las temporales son de sesión y no sobreviven al pool de conexiones, pero se
-- borran igual para no arrastrar basura en la conexión que corra la migración.

DROP TABLE tmp_v16_imagenes;
DROP TABLE tmp_v16_preparaciones;
DROP TABLE tmp_v16_videos;
DROP TABLE tmp_v16_otras_imagenes;
DROP TABLE tmp_v16_otros_videos;
DROP TABLE tmp_v16_otras_preparaciones;
DROP TABLE tmp_v16_partes;
DROP TABLE tmp_v16_moringa;
