-- V17__correcciones_moringa_videos_bebida_y_fecha_observacion.sql
--
-- Corrige tres cosas del contenido de Moringa. V16 ya está aplicada en la base
-- de desarrollo (flyway_schema_history: versión 16, checksum 731378187) y por
-- lo tanto es inmutable: editar su archivo rompería el checksum y la
-- aplicación no levantaría para nadie. Igual que hizo V12 con V10/V11, la
-- corrección va en una migración nueva y V16 no se toca.
--
-- Qué corrige
-- ===========
--
-- 1. Dos de los cuatro videos que V16 metió en video_especie van en realidad a
--    nivel de PREPARACIÓN, no de especie. Se mueven a video_preparacion:
--
--      'Té de moringa'              -> preparación 'Infusión de hoja (té)'
--      'Bebida de papaya y moringa' -> preparación 'Bebida de hoja con papaya'
--                                      (nueva, ver punto 3)
--
--    Mover = INSERT en video_preparacion y DELETE en video_especie. Es un
--    DELETE y no una baja lógica (activo = false) a propósito: estas dos filas
--    nunca debieron existir a nivel de especie, no son historial que valga la
--    pena conservar. Es el mismo criterio de V12 con los roles que sobraban.
--    La baja lógica queda para dar de baja contenido que sí fue válido.
--
-- 2. El cuarto video estaba mal titulado. V16 lo registró como 'Bebida de
--    moringa y huevo con moringa'; el video (url ...v=lSRYa_mPA5k) es solo una
--    bebida de papaya con moringa, sin huevo. El título y la descripción
--    correctos entran con el INSERT en video_preparacion, así que no hace
--    falta ningún UPDATE: la fila vieja de video_especie se borra completa.
--
-- 3. Para que ese video tenga una preparación a la que pertenecer se crea una
--    octava preparación de Moringa, sobre la parte Hoja:
--    'Bebida de hoja con papaya', tipo_consumo BEBIDA.
--
--    OJO, ESTO HAY QUE REVISARLO: el nombre, la descripción y el procedimiento
--    de esa preparación NO vienen de la bitácora de Agronomía ni de ninguna
--    fuente del proyecto. Los redacté a partir del título del video, porque
--    procedimiento es NOT NULL y la preparación no puede quedar vacía. Si el
--    video dice otra cosa (otras proporciones, hoja seca en polvo en vez de
--    hoja fresca, o si la bebida lleva la hoja cocida), hay que corregir el
--    texto en una migración posterior. Lo que sí está verificado contra la
--    base es la forma: BEBIDA es un valor real de ck_preparacion_tipo (y hasta
--    ahora no se usaba), y Hoja es la parte de Moringa que se usa en bebidas.
--
--    requiere_coccion = FALSE en esta preparación, al contrario de las siete
--    de V16, que van todas en TRUE: es un licuado, no se cuece nada. Si en el
--    video la hoja se hierve antes, hay que ponerlo en TRUE.
--
--    ingredientes, tiempo_preparacion_min y numero_porciones quedan en NULL,
--    igual que en las siete de V16, por consistencia y para no inventar
--    cantidades. orden_presentacion = 4 porque la parte Hoja ya tiene las
--    posiciones 1, 2 y 3 ocupadas por las preparaciones de V16.
--
-- 4. La ubicación de Moringa recibe su fecha de observación: 2026-08-07.
--
--    V14 había dejado fecha_observacion en NULL en las 15 especies, y lo
--    documentó (su nota del bloque 7: "La bitácora de campo no traía GPS, por
--    eso fecha_observacion queda en NULL y no se inventa una fecha de
--    recolecta"). Esta migración NO rompe ese criterio: la fecha no se deduce
--    ni se aproxima, la aportó Agronomía (observación del 7 de agosto de
--    2026). Las otras 14 especies siguen en NULL, porque de ellas no hay
--    fecha, y la aserción 7.6 lo comprueba.
--
--    Solo se escribe esa columna. El SET lista fecha_observacion y nada más,
--    y la aserción 7.6 compara la fila de Moringa contra la foto del bloque 2
--    columna por columna para demostrar que id_distrito, latitud, longitud
--    (las que dispersó V15), nombre_lugar, descripcion, activa y
--    fecha_registro quedaron igual.
--
-- 5. Los OTROS DOS videos de V16 se quedan donde están, a nivel de especie, y
--    esta migración no los toca:
--
--      '¿Cómo se come la moringa? Hojas, flores, raíces'
--      'Todo sobre la moringa: propiedades, usos y cómo tomarla'
--
--    No son de una preparación: el primero recorre varias partes de la planta
--    y el segundo habla de propiedades y usos en general. Ponerlos en una
--    preparación cualquiera sería afirmar una relación que no existe. La
--    aserción 7.4 comprueba que Moringa quedó con exactamente esos dos en
--    video_especie, ni más ni menos.
--
--
-- Decisiones de forma
-- ===================
--
-- a. Clave natural y una sola resolución, igual que V15 y V16: la especie por
--    lower(nombre_cientifico), las partes por parte_planta.nombre y las
--    preparaciones por su nombre DENTRO de las partes de Moringa. Ese último
--    filtro no es adorno: preparacion_consumo.nombre solo es único por parte
--    (uk_preparacion_parte_nombre es (id_especie_parte, nombre)), así que otra
--    especie podría tener una preparación llamada igual.
--
-- b. Idempotencia. El INSERT de la preparación y el de los videos llevan
--    NOT EXISTS, el DELETE va por clave natural y el UPDATE de la fecha es
--    escribir el mismo valor: correr el script dos veces no duplica, no borra
--    de más y no cambia nada la segunda vez.
--
-- c. El DELETE de video_especie está condicionado a que el video YA esté
--    guardado en video_preparacion (el EXISTS del bloque 5). Así, si por
--    cualquier razón el INSERT del bloque 4 no hubiera insertado, el DELETE
--    tampoco borra y no se pierde el video. La verificación del bloque 7
--    además haría fallar la migración completa.
--
-- d. video_preparacion no tiene columna de orden ni de plataforma, igual que
--    video_especie (verificado con \d: id_video_preparacion, id_preparacion,
--    titulo, descripcion, url_video, duracion_segundos, creditos, activo,
--    fecha_registro). La limitación de la nota 7 de V16 sigue vigente. Su
--    columna de baja lógica también es "activo", masculino.
--
-- e. fecha_registro no se escribe nunca: NOT NULL con DEFAULT CURRENT_TIMESTAMP.


-- =====================================================================
-- 1. Resolución de la especie y de sus partes
-- =====================================================================

CREATE TEMP TABLE tmp_v17_moringa (
    id_especie bigint PRIMARY KEY
);

INSERT INTO tmp_v17_moringa (id_especie)
SELECT e.id_especie
FROM especie e
WHERE lower(e.nombre_cientifico) = lower('Moringa oleifera');

CREATE TEMP TABLE tmp_v17_partes (
    parte            varchar(80) PRIMARY KEY,
    id_especie_parte bigint NOT NULL UNIQUE
);

INSERT INTO tmp_v17_partes (parte, id_especie_parte)
SELECT pp.nombre, epc.id_especie_parte
FROM especie_parte_comestible epc
JOIN tmp_v17_moringa m ON m.id_especie = epc.id_especie
JOIN parte_planta pp   ON pp.id_parte_planta = epc.id_parte_planta
WHERE pp.nombre IN ('Hoja', 'Flor', 'Semilla');

DO $$
DECLARE
    v_especies    integer;
    v_partes      integer;
    v_ubicaciones integer;
BEGIN
    SELECT count(*) INTO v_especies FROM tmp_v17_moringa;
    IF v_especies <> 1 THEN
        RAISE EXCEPTION
            'V17: se esperaba 1 especie con nombre_cientifico Moringa oleifera y hay %', v_especies;
    END IF;

    SELECT count(*) INTO v_partes FROM tmp_v17_partes;
    IF v_partes <> 3 THEN
        RAISE EXCEPTION
            'V17: se esperaban 3 partes comestibles de Moringa (Hoja, Flor, Semilla) y se resolvieron %', v_partes;
    END IF;

    -- El UPDATE del bloque 6 va por id_especie, no por id_ubicacion: si
    -- Moringa tuviera más de una ubicación, les escribiría la fecha a todas.
    -- Hoy tiene una sola; si eso cambia, esta migración debe fallar y no
    -- adivinar a cuál de las ubicaciones corresponde la observación.
    SELECT count(*) INTO v_ubicaciones
    FROM ubicacion_especie ue
    JOIN tmp_v17_moringa m ON m.id_especie = ue.id_especie;

    IF v_ubicaciones <> 1 THEN
        RAISE EXCEPTION
            'V17: Moringa deberia tener exactamente 1 ubicacion para poder fecharla y tiene %', v_ubicaciones;
    END IF;
END $$;


-- =====================================================================
-- 2. Foto del estado anterior
-- =====================================================================
-- Las cuatro tablas completas, con todas sus columnas. El bloque 7 las compara
-- contra el estado final y exige que la única diferencia sea exactamente lo
-- que esta migración dice que hace: una preparación más, dos videos menos en
-- video_especie, dos videos más en video_preparacion y una fecha de
-- observación en la ubicación de Moringa. Nada más.

CREATE TEMP TABLE tmp_v17_pc_antes AS SELECT * FROM preparacion_consumo;
CREATE TEMP TABLE tmp_v17_ve_antes AS SELECT * FROM video_especie;
CREATE TEMP TABLE tmp_v17_vp_antes AS SELECT * FROM video_preparacion;
CREATE TEMP TABLE tmp_v17_ue_antes AS SELECT * FROM ubicacion_especie;


-- =====================================================================
-- 3. Preparación nueva: bebida de hoja con papaya
-- =====================================================================
-- Ver el punto 3 del encabezado: el texto es redacción propia a partir del
-- título del video, no de la bitácora. Está marcado ahí para que se revise.

INSERT INTO preparacion_consumo (id_especie_parte, nombre, descripcion, tipo_consumo,
                                 requiere_coccion, ingredientes, procedimiento,
                                 tiempo_preparacion_min, numero_porciones, advertencias,
                                 fuente_tradicional, orden_presentacion, activa)
SELECT pt.id_especie_parte,
       'Bebida de hoja con papaya',
       'Hoja de moringa licuada con papaya, consumida como bebida fría.',
       'BEBIDA',
       false,
       NULL,
       'Lavar la hoja de moringa y licuarla con pulpa de papaya y agua; colar si se desea y servir frío.',
       NULL,
       NULL,
       NULL,
       'Uso culinario tradicional',
       4,
       true
FROM tmp_v17_partes pt
WHERE pt.parte = 'Hoja'
  AND NOT EXISTS (
      SELECT 1 FROM preparacion_consumo p
      WHERE p.id_especie_parte = pt.id_especie_parte
        AND p.nombre = 'Bebida de hoja con papaya'
  );


-- =====================================================================
-- 4. Los dos videos, ahora a nivel de preparación
-- =====================================================================
-- titulo y descripcion viajan aquí con su texto CORREGIDO (el del video de
-- papaya ya no menciona huevo). El JOIN contra tmp_v17_partes es lo que
-- garantiza que la preparación encontrada sea la de Moringa y no una de otra
-- especie que se llame igual.

CREATE TEMP TABLE tmp_v17_videos (
    url_video          varchar(1000) PRIMARY KEY,
    nombre_preparacion varchar(150)  NOT NULL,
    titulo             varchar(200)  NOT NULL,
    descripcion        text          NOT NULL,
    orden              smallint      NOT NULL
);

INSERT INTO tmp_v17_videos (url_video, nombre_preparacion, titulo, descripcion, orden)
VALUES
    ('https://www.youtube.com/watch?v=E_q-l-peipU',
     'Infusión de hoja (té)',
     'Té de moringa',
     'Preparación de té (infusión) de moringa.', 1),

    ('https://www.youtube.com/watch?v=lSRYa_mPA5k',
     'Bebida de hoja con papaya',
     'Bebida de papaya y moringa',
     'Preparación de una bebida de papaya con moringa.', 2);

INSERT INTO video_preparacion (id_preparacion, titulo, descripcion, url_video,
                               duracion_segundos, creditos, activo)
SELECT p.id_preparacion,
       d.titulo,
       d.descripcion,
       d.url_video,
       NULL,
       NULL,
       true
FROM tmp_v17_videos d
JOIN preparacion_consumo p ON p.nombre = d.nombre_preparacion
JOIN tmp_v17_partes pt     ON pt.id_especie_parte = p.id_especie_parte
WHERE NOT EXISTS (
    SELECT 1 FROM video_preparacion v
    WHERE v.id_preparacion = p.id_preparacion
      AND v.url_video = d.url_video
)
ORDER BY d.orden;


-- =====================================================================
-- 5. Baja de las dos filas que quedaron mal en video_especie
-- =====================================================================
-- El EXISTS es la red de la decisión (c) del encabezado: solo se borra el
-- video de la especie si ya quedó guardado en la preparación que le toca.

DELETE FROM video_especie v
USING tmp_v17_videos d, tmp_v17_moringa m
WHERE v.id_especie = m.id_especie
  AND v.url_video  = d.url_video
  AND EXISTS (
      SELECT 1
      FROM video_preparacion vp
      JOIN preparacion_consumo p ON p.id_preparacion = vp.id_preparacion
      JOIN tmp_v17_partes pt     ON pt.id_especie_parte = p.id_especie_parte
      WHERE vp.url_video = d.url_video
        AND p.nombre     = d.nombre_preparacion
  );


-- =====================================================================
-- 6. Fecha de observación de la ubicación de Moringa
-- =====================================================================
-- 2026-08-07, aportada por Agronomía (ver punto 4 del encabezado). El SET
-- tiene una sola columna, así que es imposible que este UPDATE toque las
-- coordenadas que dispersó V15 ni el distrito.

UPDATE ubicacion_especie ue
SET fecha_observacion = DATE '2026-08-07'
FROM tmp_v17_moringa m
WHERE ue.id_especie = m.id_especie;


-- =====================================================================
-- 7. Verificación
-- =====================================================================
-- Los INSERT resuelven sus FK por JOIN y un JOIN sin match no falla: solo no
-- inserta. Sin este bloque, un nombre de preparación mal escrito dejaría el
-- video sin mover (o peor: borrado de un lado sin estar en el otro) y la
-- migración se marcaría como exitosa. Cualquier fallo revierte todo.

DO $$
DECLARE
    v_bebida       integer;
    v_preps        integer;
    v_hoja         integer;
    v_vp_moringa   integer;
    v_vp_correctos integer;
    v_ve_moringa   integer;
    v_ve_sobrantes integer;
    v_ue_moringa   integer;
    v_ue_otras     integer;
    v_pc_resto     integer;
    v_ve_resto     integer;
    v_vp_resto     integer;
    v_ue_resto     integer;
BEGIN
    -- 7.1 La preparación nueva existe, sobre Hoja y con la forma pedida.
    SELECT count(*) INTO v_bebida
    FROM preparacion_consumo p
    JOIN tmp_v17_partes pt ON pt.id_especie_parte = p.id_especie_parte
    WHERE pt.parte = 'Hoja'
      AND p.nombre = 'Bebida de hoja con papaya'
      AND p.tipo_consumo = 'BEBIDA'
      AND p.requiere_coccion = false
      AND p.orden_presentacion = 4
      AND p.activa;

    IF v_bebida <> 1 THEN
        RAISE EXCEPTION
            'V17: se esperaba 1 preparacion Bebida de hoja con papaya en la Hoja de Moringa (BEBIDA, sin coccion, orden 4) y hay %', v_bebida;
    END IF;

    -- 7.2 Moringa quedó con 8 preparaciones (las 7 de V16 + la bebida), 4 de
    --     ellas en Hoja.
    SELECT count(*) INTO v_preps
    FROM preparacion_consumo p
    JOIN tmp_v17_partes pt ON pt.id_especie_parte = p.id_especie_parte;

    IF v_preps <> 8 THEN
        RAISE EXCEPTION
            'V17: Moringa deberia tener 8 preparaciones (7 de V16 + la bebida) y tiene %', v_preps;
    END IF;

    SELECT count(*) INTO v_hoja
    FROM preparacion_consumo p
    JOIN tmp_v17_partes pt ON pt.id_especie_parte = p.id_especie_parte
    WHERE pt.parte = 'Hoja';

    IF v_hoja <> 4 THEN
        RAISE EXCEPTION
            'V17: la Hoja de Moringa deberia tener 4 preparaciones y tiene %', v_hoja;
    END IF;

    -- 7.3 Los dos videos quedaron en video_preparacion, cada uno colgado de la
    --     preparación que le toca y con el texto corregido.
    SELECT count(*) INTO v_vp_moringa
    FROM video_preparacion vp
    JOIN preparacion_consumo p ON p.id_preparacion = vp.id_preparacion
    JOIN tmp_v17_partes pt     ON pt.id_especie_parte = p.id_especie_parte;

    IF v_vp_moringa <> 2 THEN
        RAISE EXCEPTION
            'V17: las preparaciones de Moringa deberian tener 2 videos en total y tienen %', v_vp_moringa;
    END IF;

    SELECT count(*) INTO v_vp_correctos
    FROM tmp_v17_videos d
    JOIN preparacion_consumo p ON p.nombre = d.nombre_preparacion
    JOIN tmp_v17_partes pt     ON pt.id_especie_parte = p.id_especie_parte
    JOIN video_preparacion vp  ON vp.id_preparacion = p.id_preparacion
                              AND vp.url_video = d.url_video
    WHERE vp.titulo = d.titulo
      AND vp.descripcion = d.descripcion
      AND vp.activo;

    IF v_vp_correctos <> 2 THEN
        RAISE EXCEPTION
            'V17: se esperaban los 2 videos ligados a su preparacion con el titulo corregido y calzan % (revisar los nombres de preparacion)',
            v_vp_correctos;
    END IF;

    -- 7.4 A Moringa le quedan exactamente los 2 videos generales a nivel de
    --     especie, y ninguno de los dos movidos.
    SELECT count(*) INTO v_ve_moringa
    FROM video_especie v
    JOIN tmp_v17_moringa m ON m.id_especie = v.id_especie;

    IF v_ve_moringa <> 2 THEN
        RAISE EXCEPTION
            'V17: a Moringa deberian quedarle 2 videos a nivel de especie (los generales) y tiene %', v_ve_moringa;
    END IF;

    SELECT count(*) INTO v_ve_sobrantes
    FROM video_especie v
    JOIN tmp_v17_moringa m ON m.id_especie = v.id_especie
    JOIN tmp_v17_videos d  ON d.url_video = v.url_video;

    IF v_ve_sobrantes > 0 THEN
        RAISE EXCEPTION
            'V17: % video(s) movido(s) siguen colgados de la especie en video_especie', v_ve_sobrantes;
    END IF;

    -- 7.5 La ubicación de Moringa quedó fechada y NADA más de esa fila cambió:
    --     se compara contra la foto del bloque 2 columna por columna, así que
    --     el distrito y las coordenadas de V15 quedan demostrados, no supuestos.
    SELECT count(*) INTO v_ue_moringa
    FROM ubicacion_especie ue
    JOIN tmp_v17_moringa m   ON m.id_especie = ue.id_especie
    JOIN tmp_v17_ue_antes a  ON a.id_ubicacion = ue.id_ubicacion
    WHERE ue.fecha_observacion = DATE '2026-08-07'
      AND ue.id_distrito    = a.id_distrito
      AND ue.latitud        = a.latitud
      AND ue.longitud       = a.longitud
      AND ue.activa         = a.activa
      AND ue.fecha_registro = a.fecha_registro
      AND ue.nombre_lugar IS NOT DISTINCT FROM a.nombre_lugar
      AND ue.descripcion  IS NOT DISTINCT FROM a.descripcion;

    IF v_ue_moringa <> 1 THEN
        RAISE EXCEPTION
            'V17: se esperaba 1 ubicacion de Moringa fechada 2026-08-07 y con el resto de sus columnas intactas, y calzan %', v_ue_moringa;
    END IF;

    -- Una observación de campo no puede ser futura: atrapa un typo en el año.
    IF DATE '2026-08-07' > CURRENT_DATE THEN
        RAISE EXCEPTION
            'V17: la fecha de observacion 2026-08-07 es posterior a hoy (%); revisar el dato', CURRENT_DATE;
    END IF;

    -- 7.6 Las otras 14 especies siguen SIN fecha de observación: de ellas no
    --     hay dato y V17 no se la inventa (ver punto 4 del encabezado).
    SELECT count(*) INTO v_ue_otras
    FROM ubicacion_especie ue
    WHERE ue.id_especie NOT IN (SELECT id_especie FROM tmp_v17_moringa)
      AND ue.fecha_observacion IS NOT NULL;

    IF v_ue_otras > 0 THEN
        RAISE EXCEPTION
            'V17: % ubicacion(es) de otras especies quedaron con fecha_observacion; solo la de Moringa debia recibirla',
            v_ue_otras;
    END IF;

    -- 7.7 Nada más cambió. En las cuatro tablas, lo que queda después de
    --     descontar exactamente lo que esta migración dice que hace tiene que
    --     ser idéntico a la foto del bloque 2. La diferencia simétrica vale 0
    --     solo si no cambió ningún valor, no se perdió ninguna fila y no
    --     apareció ninguna de más.

    -- preparacion_consumo: antes == después menos la bebida nueva.
    SELECT count(*) INTO v_pc_resto FROM (
        (SELECT * FROM preparacion_consumo
          WHERE NOT (nombre = 'Bebida de hoja con papaya'
                     AND id_especie_parte IN (SELECT id_especie_parte FROM tmp_v17_partes))
         EXCEPT ALL
         SELECT * FROM tmp_v17_pc_antes)
        UNION ALL
        (SELECT * FROM tmp_v17_pc_antes
         EXCEPT ALL
         SELECT * FROM preparacion_consumo
          WHERE NOT (nombre = 'Bebida de hoja con papaya'
                     AND id_especie_parte IN (SELECT id_especie_parte FROM tmp_v17_partes)))
    ) q;

    IF v_pc_resto > 0 THEN
        RAISE EXCEPTION
            'V17: % fila(s) de preparacion_consumo cambiaron ademas de la bebida nueva; esta migracion solo debia agregar esa',
            v_pc_resto;
    END IF;

    -- video_especie: después == antes menos los dos videos movidos. Esto es lo
    -- que protege a las otras 14 especies y a los 2 videos generales de Moringa.
    SELECT count(*) INTO v_ve_resto FROM (
        (SELECT * FROM video_especie
         EXCEPT ALL
         SELECT * FROM tmp_v17_ve_antes
          WHERE NOT (id_especie IN (SELECT id_especie FROM tmp_v17_moringa)
                     AND url_video IN (SELECT url_video FROM tmp_v17_videos)))
        UNION ALL
        (SELECT * FROM tmp_v17_ve_antes
          WHERE NOT (id_especie IN (SELECT id_especie FROM tmp_v17_moringa)
                     AND url_video IN (SELECT url_video FROM tmp_v17_videos))
         EXCEPT ALL
         SELECT * FROM video_especie)
    ) q;

    IF v_ve_resto > 0 THEN
        RAISE EXCEPTION
            'V17: % fila(s) de video_especie cambiaron ademas de los 2 videos movidos de Moringa',
            v_ve_resto;
    END IF;

    -- video_preparacion: antes == después menos los dos videos nuevos. Esto es
    -- lo que protege al video de Portulaca oleracea que ya estaba.
    SELECT count(*) INTO v_vp_resto FROM (
        (SELECT * FROM video_preparacion
          WHERE NOT (url_video IN (SELECT url_video FROM tmp_v17_videos)
                     AND id_preparacion IN (
                         SELECT p.id_preparacion FROM preparacion_consumo p
                         JOIN tmp_v17_partes pt ON pt.id_especie_parte = p.id_especie_parte))
         EXCEPT ALL
         SELECT * FROM tmp_v17_vp_antes)
        UNION ALL
        (SELECT * FROM tmp_v17_vp_antes
         EXCEPT ALL
         SELECT * FROM video_preparacion
          WHERE NOT (url_video IN (SELECT url_video FROM tmp_v17_videos)
                     AND id_preparacion IN (
                         SELECT p.id_preparacion FROM preparacion_consumo p
                         JOIN tmp_v17_partes pt ON pt.id_especie_parte = p.id_especie_parte)))
    ) q;

    IF v_vp_resto > 0 THEN
        RAISE EXCEPTION
            'V17: % fila(s) de video_preparacion cambiaron ademas de los 2 videos nuevos de Moringa',
            v_vp_resto;
    END IF;

    -- ubicacion_especie: las filas que NO son de Moringa quedaron idénticas.
    SELECT count(*) INTO v_ue_resto FROM (
        (SELECT * FROM ubicacion_especie
          WHERE id_especie NOT IN (SELECT id_especie FROM tmp_v17_moringa)
         EXCEPT ALL
         SELECT * FROM tmp_v17_ue_antes
          WHERE id_especie NOT IN (SELECT id_especie FROM tmp_v17_moringa))
        UNION ALL
        (SELECT * FROM tmp_v17_ue_antes
          WHERE id_especie NOT IN (SELECT id_especie FROM tmp_v17_moringa)
         EXCEPT ALL
         SELECT * FROM ubicacion_especie
          WHERE id_especie NOT IN (SELECT id_especie FROM tmp_v17_moringa))
    ) q;

    IF v_ue_resto > 0 THEN
        RAISE EXCEPTION
            'V17: % fila(s) de ubicacion_especie de otras especies cambiaron; solo la de Moringa debia recibir fecha',
            v_ue_resto;
    END IF;
END $$;


-- =====================================================================
-- 8. Limpieza
-- =====================================================================

DROP TABLE tmp_v17_videos;
DROP TABLE tmp_v17_pc_antes;
DROP TABLE tmp_v17_ve_antes;
DROP TABLE tmp_v17_vp_antes;
DROP TABLE tmp_v17_ue_antes;
DROP TABLE tmp_v17_partes;
DROP TABLE tmp_v17_moringa;
