-- V15__dispersar_coordenadas_ubicacion_especie.sql
--
-- Separa en el mapa las especies que V14 dejó compartiendo el mismo punto.
-- Después de V14, las 15 ubicaciones caían en solo 4 coordenadas (6 especies
-- en el centroide de Guadalupe, 5 en el de Tecoluca, 2 en San Vicente y 2 en
-- Cojutepeque), así que en el mapa se veían como un único marcador por zona.
-- Esta migración les da un desplazamiento pequeño alrededor de ese mismo
-- centroide para que cada especie tenga su propio marcador.
--
-- ALCANCE: solo latitud y longitud. El UPDATE del bloque 3 lista exactamente
-- esas dos columnas en su SET, así que id_distrito, nombre_lugar, activa,
-- descripcion, fecha_observacion y fecha_registro quedan tal como están.
--
-- Magnitud del movimiento, medida contra las coordenadas que dejó V14: entre
-- 127 m (Portulaca oleracea) y 1,128 m (Brosimum alicastrum), con la mayoría
-- entre 380 y 800 m.
--
-- OJO con una limitación real: la base NO guarda los polígonos de los
-- distritos, solo el id_distrito como llave foránea. Por lo tanto NADA en este
-- script puede comprobar que el punto nuevo caiga de verdad dentro del
-- distrito asignado; la aserción 4.3 solo demuestra que el id_distrito quedó
-- igual que antes. Son desplazamientos pequeños frente al tamaño de estos
-- distritos, pero si alguna coordenada se corrige a mano más adelante, la
-- correspondencia punto-distrito hay que revisarla en un mapa, no aquí.
--
--
-- Decisiones
-- ==========
--
-- 1. Clave natural. Cada ubicación se encuentra por el nombre científico de su
--    especie (uk_especie_nombre_cientifico_lower), nunca por id. Verificado
--    contra la base: hay exactamente una fila de ubicacion_especie por especie
--    (15 filas / 15 especies / máximo 1 por especie), así que el UPDATE no
--    puede tocar más de una fila por especie.
--
-- 2. Las 15 coordenadas destino viven en UNA sola tabla temporal
--    (tmp_v15_coordenadas), y de ahí las leen tanto el UPDATE como el bloque
--    de verificación. Así la lista no se escribe dos veces y no hay forma de
--    que la verificación y el UPDATE se desincronicen si alguien corrige un
--    valor.
--
-- 3. Para poder afirmar que NINGUNA ubicación cambió de distrito hace falta
--    comparar contra el estado anterior, no solo confiar en que el SET no
--    menciona id_distrito. Por eso el bloque 2 guarda una foto de
--    (id_ubicacion, id_distrito, nombre_lugar, activa) ANTES del UPDATE y el
--    bloque 4 la compara después. Es una comprobación real, no una tautología.
--
-- 4. Tipos. latitud es numeric(9,7) y longitud numeric(10,7) en la tabla real;
--    las temporales se declaran con la misma precisión y escala para que la
--    comparación del bloque 4 sea exacta y no dependa de un redondeo.
--
-- 5. Las tablas temporales se borran explícitamente en el bloque 5. Son de
--    sesión, así que no sobreviven al pool de conexiones, pero se dejan
--    borradas para no arrastrar basura en la conexión que corra la migración.
--
-- 6. Si cualquier aserción falla, el RAISE EXCEPTION del bloque 4 hace que
--    Flyway revierta la migración completa y ninguna coordenada queda a medias.


-- =====================================================================
-- 1. Coordenadas destino
-- =====================================================================
-- Única fuente de verdad de esta migración. Todas caen dentro de El Salvador
-- y dentro del distrito que cada especie ya tenía asignado desde V14.

CREATE TEMP TABLE tmp_v15_coordenadas (
    nombre_cientifico varchar(250) PRIMARY KEY,
    latitud           numeric(9,7)  NOT NULL,
    longitud          numeric(10,7) NOT NULL
);

INSERT INTO tmp_v15_coordenadas (nombre_cientifico, latitud, longitud)
VALUES
    -- San Vicente (alrededor de 13.6417 / -88.7842)
    ('Bactris guineensis',        13.6465, -88.7895),
    ('Bromelia pinguin',          13.6370, -88.7795),

    -- Cojutepeque (alrededor de 13.7167 / -88.9333)
    ('Chamaedorea tepejilote',    13.7205, -88.9370),
    ('Cymbopogon citratus',       13.7130, -88.9298),

    -- Guadalupe (alrededor de 13.5897 / -88.8503)
    ('Sinclairia sublobata',      13.5935, -88.8530),
    ('Cnidoscolus aconitifolius', 13.5918, -88.8475),
    ('Byrsonima crassifolia',     13.5880, -88.8535),
    ('Averrhoa carambola',        13.5865, -88.8480),
    ('Portulaca oleracea',        13.5908, -88.8500),
    ('Spondias purpurea',         13.5855, -88.8512),

    -- Tecoluca (alrededor de 13.3300 / -88.7300)
    ('Brosimum alicastrum',       13.3375, -88.7370),
    ('Moringa oleifera',          13.3340, -88.7255),
    ('Cordia dentata',            13.3270, -88.7345),
    ('Leucaena leucocephala',     13.3235, -88.7270),
    ('Capsicum annuum',           13.3310, -88.7225);


-- =====================================================================
-- 2. Foto del estado anterior
-- =====================================================================
-- Se guarda lo que NO debe cambiar, para poder demostrarlo en el bloque 4.

CREATE TEMP TABLE tmp_v15_antes AS
SELECT ue.id_ubicacion,
       ue.id_especie,
       ue.id_distrito,
       ue.nombre_lugar,
       ue.activa
FROM ubicacion_especie ue;


-- =====================================================================
-- 3. Actualización de coordenadas
-- =====================================================================
-- SET con dos columnas y nada más: latitud y longitud. id_distrito NO aparece
-- aquí, así que es imposible que el UPDATE lo modifique.
--
-- El uk_ubicacion_especie_coords_activa es UNIQUE (id_especie, latitud,
-- longitud) WHERE activa: como cada especie tiene una sola ubicación y cada
-- especie recibe un punto distinto, no hay choque posible.

UPDATE ubicacion_especie ue
SET latitud  = c.latitud,
    longitud = c.longitud
FROM tmp_v15_coordenadas c
JOIN especie e ON lower(e.nombre_cientifico) = lower(c.nombre_cientifico)
WHERE ue.id_especie = e.id_especie;


-- =====================================================================
-- 4. Verificación
-- =====================================================================
-- Un UPDATE ... FROM cuyo JOIN no encuentra su fila no falla: simplemente
-- actualiza menos filas de las esperadas. Sin este bloque, un nombre
-- científico mal escrito dejaría parte del mapa sin dispersar y la migración
-- se marcaría como exitosa.

DO $$
DECLARE
    v_total               integer;
    v_coinciden           integer;
    v_fuera_rango         integer;
    v_distrito_cambiado   integer;
    v_otras_col_cambiadas integer;
    v_filas_perdidas      integer;
BEGIN
    -- 4.1 Las 15 filas quedaron exactamente con las coordenadas destino.
    SELECT count(*) INTO v_total FROM ubicacion_especie;

    SELECT count(*) INTO v_coinciden
    FROM ubicacion_especie ue
    JOIN especie e ON e.id_especie = ue.id_especie
    JOIN tmp_v15_coordenadas c
         ON lower(c.nombre_cientifico) = lower(e.nombre_cientifico)
    WHERE ue.latitud = c.latitud
      AND ue.longitud = c.longitud;

    IF v_coinciden <> 15 THEN
        RAISE EXCEPTION
            'V15: se esperaban 15 ubicaciones con las coordenadas nuevas y hay % (de % filas en la tabla). Revisar los nombres cientificos.',
            v_coinciden, v_total;
    END IF;

    IF v_total <> 15 THEN
        RAISE EXCEPTION 'V15: ubicacion_especie deberia tener 15 filas y tiene %', v_total;
    END IF;

    -- 4.2 Todo dentro de El Salvador.
    SELECT count(*) INTO v_fuera_rango
    FROM ubicacion_especie
    WHERE latitud  NOT BETWEEN 13.1  AND 14.5
       OR longitud NOT BETWEEN -90.2 AND -87.6;

    IF v_fuera_rango > 0 THEN
        RAISE EXCEPTION
            'V15: % ubicacion(es) quedaron fuera del rango de El Salvador (lat 13.1..14.5, lon -90.2..-87.6)',
            v_fuera_rango;
    END IF;

    -- 4.3 Ninguna ubicacion cambio de distrito respecto a antes del UPDATE.
    SELECT count(*) INTO v_distrito_cambiado
    FROM ubicacion_especie ue
    JOIN tmp_v15_antes a ON a.id_ubicacion = ue.id_ubicacion
    WHERE ue.id_distrito <> a.id_distrito;

    IF v_distrito_cambiado > 0 THEN
        RAISE EXCEPTION
            'V15: % ubicacion(es) cambiaron de id_distrito; esta migracion solo debe mover coordenadas',
            v_distrito_cambiado;
    END IF;

    -- 4.4 Tampoco se toco nombre_lugar ni activa (la tarea pedia dejarlos igual).
    SELECT count(*) INTO v_otras_col_cambiadas
    FROM ubicacion_especie ue
    JOIN tmp_v15_antes a ON a.id_ubicacion = ue.id_ubicacion
    WHERE ue.nombre_lugar IS DISTINCT FROM a.nombre_lugar
       OR ue.activa       IS DISTINCT FROM a.activa;

    IF v_otras_col_cambiadas > 0 THEN
        RAISE EXCEPTION
            'V15: % ubicacion(es) cambiaron nombre_lugar o activa; solo debian cambiar latitud y longitud',
            v_otras_col_cambiadas;
    END IF;

    -- 4.5 No se perdio ni se agrego ninguna fila.
    SELECT count(*) INTO v_filas_perdidas
    FROM tmp_v15_antes a
    WHERE NOT EXISTS (
        SELECT 1 FROM ubicacion_especie ue WHERE ue.id_ubicacion = a.id_ubicacion
    );

    IF v_filas_perdidas > 0 THEN
        RAISE EXCEPTION 'V15: se perdieron % filas de ubicacion_especie', v_filas_perdidas;
    END IF;
END $$;


-- =====================================================================
-- 5. Limpieza
-- =====================================================================

DROP TABLE tmp_v15_coordenadas;
DROP TABLE tmp_v15_antes;
