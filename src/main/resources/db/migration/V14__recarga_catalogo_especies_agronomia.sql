-- V14__recarga_catalogo_especies_agronomia.sql
--
-- Reemplaza por completo el catálogo de especies: borra las especies actuales
-- (datos de prueba de V9 y de las pruebas de QA) con todo su contenido
-- asociado, y carga las 15 plantas levantadas por Agronomía, cada una con su
-- taxonomía, nombre común, descripción, parte comestible, imagen, ubicación,
-- fuente y aporte nutricional.
--
-- Esta migración NO toca catálogos ni otros dominios: parte_planta, habitat,
-- beneficio, nutriente, departamento/municipio/distrito y el módulo de
-- seguridad (usuario, rol, permiso) quedan intactos. De `fuente` y `nutriente`
-- solo se agregan las filas que falten.
--
--
-- Decisiones que aplican a todo el script
-- =======================================
--
-- 1. Ninguna PK se escribe literal. Todas son GENERATED ALWAYS AS IDENTITY y
--    cada llave foránea se resuelve por su clave natural con una subconsulta
--    o un JOIN, igual que V4/V7/V9:
--      especie        -> lower(nombre_cientifico)  (uk_especie_nombre_cientifico_lower)
--      taxonomia      -> (reino, genero, especie_taxonomica, subespecie)  (uk_taxonomia)
--      parte_planta   -> nombre        (parte_planta_nombre_key)
--      nutriente      -> nombre        (nutriente_nombre_key)
--      fuente         -> lower(titulo) (`fuente` NO tiene constraint única)
--      distrito       -> (departamento, municipio, distrito) por nombre
--      usuario        -> nombre_usuario (uk_usuario_nombre_lower)
--    Por eso tampoco hace falta ningún setval: nunca se inserta un id.
--
-- 2. Nombres de columna verificados uno por uno contra la base real. Los que
--    no son obvios y conviene tener presentes:
--      especie_parte_comestible.id_parte_planta  (no "id_parte")
--      fuente.tipo_fuente                        (no "tipo")
--      fuente.activa / especie.activa            (femenino)
--      parte_planta.activo / nutriente.activo    (masculino)
--      taxonomia NO tiene columna de activo ni nombre_cientifico
--
-- 3. Valores de enum tomados de los CHECK de la base (y confirmados contra
--    las clases de entity/enums, que se persisten como texto):
--      especie.estado_publicacion -> BORRADOR, EN_REVISION, PUBLICADA, RECHAZADA
--      imagen_especie.tipo_imagen -> PLANTA_COMPLETA, HOJA, FLOR, FRUTO,
--                                    SEMILLA, RAIZ, TALLO, CORTEZA,
--                                    PARTE_COMESTIBLE, OTRA
--      fuente.tipo_fuente         -> LIBRO, ARTICULO, SITIO_WEB, ENTREVISTA,
--                                    TESIS, INFORME, CONOCIMIENTO_ANCESTRAL, OTRO
--      nutriente.categoria        -> MACRONUTRIENTE, VITAMINA, MINERAL,
--                                    ENERGIA, FIBRA, OTRO
--
--    Dos valores pedidos en la bitácora NO existen en el esquema y se
--    tradujeron al valor real más cercano:
--
--      a) tipo_imagen: no hay ningún valor "GENERAL". Las 15 imágenes son
--         fotografías generales de la planta, con id_especie_parte NULL, así
--         que se usa PLANTA_COMPLETA, que es el valor del enum que cubre ese
--         caso (la alternativa, OTRA, perdería la semántica).
--
--      b) tipo_fuente: no hay ningún valor "CIENTIFICA". Se clasifica cada
--         fuente por su soporte real:
--           F1 USDA FoodData Central  -> SITIO_WEB (base de datos en línea)
--           F2 artículo en PMC (2025) -> ARTICULO
--           F3 Purdue NewCROP         -> ARTICULO
--           F4 literatura sin URL     -> OTRO
--
-- 4. Columnas NOT NULL que la bitácora no traía y hubo que llenar:
--
--      imagen_especie.texto_alternativo (varchar(300) NOT NULL). Se compone
--      del nombre común y el científico, que sí vienen en los datos; no se
--      describe el contenido de la foto para no afirmar nada que no conste.
--
--    En cambio especie.propiedades, especie.importancia_cultural y
--    especie.advertencias SÍ admiten NULL (verificado), así que quedan en NULL
--    y no se inventa texto de relleno.
--
-- 5. Estado de publicación: PUBLICADA para las 15 especies, así que salen de
--    una vez en el catálogo público. El CHECK ck_especie_publicacion exige que
--    una especie PUBLICADA traiga validada_por, publicada_por y
--    fecha_publicacion; ck_especie_validacion exige fecha_validacion cuando
--    hay validada_por. Los cinco campos viajan juntos:
--      creada_por   = investigador semilla (nombre_usuario 'investigador', V3)
--      validada_por = investigador semilla
--      publicada_por = admin semilla      (nombre_usuario 'admin', V3)
--    Se usan los usuarios de V3 y no los '.demo' de V9 porque V3 es la
--    semilla base del proyecto. Si alguno faltara, el subselect devolvería
--    NULL y el NOT NULL de creada_por haría fallar la migración completa en
--    vez de dejar datos a medias.
--
-- 6. Fechas. fecha_registro y fecha_actualizacion no se insertan: las columnas
--    tienen DEFAULT CURRENT_TIMESTAMP y están anotadas @Generated en las
--    entidades, así que la base les pone la fecha de aplicación de esta
--    migración, que es justo "la fecha actual" que pedía la bitácora.
--    fecha_validacion y fecha_publicacion sí se escriben, con LOCALTIMESTAMP
--    (las columnas son timestamp without time zone).
--
-- 7. Geografía. Cuscatlán YA existe en el seed (V8 cargó los 262 distritos),
--    así que no se crea nada. Pacaya y Zacate limón se ubican en el distrito
--    de Cojutepeque (municipio Cuscatlán Sur, departamento Cuscatlán): las
--    coordenadas de la bitácora para esas dos filas, 13.7167 / -88.9333, son
--    exactamente el centroide de Cojutepeque. Los distritos de San Vicente Sur
--    (San Vicente, Guadalupe, Tecoluca) también existen desde V7/V8.
--
-- 8. Índices únicos parciales que condicionan esta carga (verificados):
--      uk_nombre_comun_principal          -> un solo nombre común principal
--                                            activo por especie
--      uk_imagen_principal_especie        -> una sola imagen principal activa
--                                            por especie
--      uk_ubicacion_especie_coords_activa -> (especie, lat, lon) única entre
--                                            las ubicaciones activas
--    Se carga exactamente un nombre común, una imagen y una ubicación por
--    especie, así que ninguno se viola.
--
-- 9. Las 15 URL de imagen miden entre 61 y 140 caracteres; url_imagen es
--    varchar(1000), así que entran sin truncarse.


-- =====================================================================
-- 1. Borrado total de las especies actuales
-- =====================================================================
-- Ninguna FK del esquema tiene ON DELETE CASCADE (verificado contra
-- pg_constraint), así que el borrado va a mano, de las hojas hacia la raíz.
--
-- Todas las tablas de este bloque son dependientes de `especie`: no contienen
-- ni una fila que pertenezca a otro dominio, por eso el DELETE va sin WHERE.
-- Las dos que se borran por tabla intermedia y conviene señalar:
--   - parte_beneficio: cuelga de especie_parte_comestible. NO estaba en la
--     lista original de la tarea, pero sin borrarla el DELETE de
--     especie_parte_comestible falla por fk_parte_beneficio_parte. Se borra la
--     asociación, NO el catálogo `beneficio`.
--   - imagen_especie / video_especie: tienen una FK compuesta
--     (id_especie_parte, id_especie) hacia especie_parte_comestible, además de
--     la FK simple hacia especie, así que van antes que ella.

-- 1.1 Nietos de especie_parte_comestible (vía preparacion_consumo)
DELETE FROM imagen_preparacion;
DELETE FROM video_preparacion;

-- 1.2 Hijos de especie_parte_comestible
DELETE FROM preparacion_consumo;
DELETE FROM aporte_nutricional;
DELETE FROM epoca_cosecha;
DELETE FROM parte_beneficio;

-- 1.3 Multimedia de especie (FK compuesta hacia especie_parte_comestible)
DELETE FROM imagen_especie;
DELETE FROM video_especie;

-- 1.4 Hijos directos de especie
DELETE FROM especie_parte_comestible;
DELETE FROM nombre_comun;
DELETE FROM ubicacion_especie;
DELETE FROM especie_habitat;
DELETE FROM especie_fuente;

-- 1.5 La especie
DELETE FROM especie;

-- 1.6 Taxonomías que quedaron huérfanas
-- Solo las que ya no tienen especie. La relación es uno a uno
-- (especie_id_taxonomia_key), así que en la práctica caen todas las que
-- existían, pero el filtro deja la sentencia correcta si alguna vez hay
-- taxonomías cargadas por adelantado.
DELETE FROM taxonomia t
WHERE NOT EXISTS (
    SELECT 1 FROM especie e WHERE e.id_taxonomia = t.id_taxonomia
);


-- =====================================================================
-- 2. Taxonomía de las 15 especies
-- =====================================================================
-- reino se escribe explícito ('Plantae', único valor que admite
-- ck_taxonomia_reino) porque forma parte de uk_taxonomia y es la clave por la
-- que el bloque 3 vuelve a encontrar cada fila. subespecie queda NULL en las
-- 15, y uk_taxonomia es NULLS NOT DISTINCT, así que el par
-- (genero, especie_taxonomica) ya identifica a cada una sin ambigüedad.

INSERT INTO taxonomia (reino, division, clase, orden_taxonomico, familia, genero, especie_taxonomica, subespecie)
VALUES
    ('Plantae', 'Magnoliophyta', 'Liliopsida',    'Arecales',       'Arecaceae',     'Bactris',     'guineensis',     NULL),
    ('Plantae', 'Magnoliophyta', 'Liliopsida',    'Poales',         'Bromeliaceae',  'Bromelia',    'pinguin',        NULL),
    ('Plantae', 'Magnoliophyta', 'Liliopsida',    'Arecales',       'Arecaceae',     'Chamaedorea', 'tepejilote',     NULL),
    ('Plantae', 'Magnoliophyta', 'Magnoliopsida', 'Asterales',      'Asteraceae',    'Sinclairia',  'sublobata',      NULL),
    ('Plantae', 'Magnoliophyta', 'Magnoliopsida', 'Malpighiales',   'Euphorbiaceae', 'Cnidoscolus', 'aconitifolius',  NULL),
    ('Plantae', 'Magnoliophyta', 'Magnoliopsida', 'Malpighiales',   'Malpighiaceae', 'Byrsonima',   'crassifolia',    NULL),
    ('Plantae', 'Magnoliophyta', 'Magnoliopsida', 'Oxalidales',     'Oxalidaceae',   'Averrhoa',    'carambola',      NULL),
    ('Plantae', 'Magnoliophyta', 'Liliopsida',    'Poales',         'Poaceae',       'Cymbopogon',  'citratus',       NULL),
    ('Plantae', 'Magnoliophyta', 'Magnoliopsida', 'Caryophyllales', 'Portulacaceae', 'Portulaca',   'oleracea',       NULL),
    ('Plantae', 'Magnoliophyta', 'Magnoliopsida', 'Sapindales',     'Anacardiaceae', 'Spondias',    'purpurea',       NULL),
    ('Plantae', 'Magnoliophyta', 'Magnoliopsida', 'Rosales',        'Moraceae',      'Brosimum',    'alicastrum',     NULL),
    ('Plantae', 'Magnoliophyta', 'Magnoliopsida', 'Brassicales',    'Moringaceae',   'Moringa',     'oleifera',       NULL),
    ('Plantae', 'Magnoliophyta', 'Magnoliopsida', 'Boraginales',    'Boraginaceae',  'Cordia',      'dentata',        NULL),
    ('Plantae', 'Magnoliophyta', 'Magnoliopsida', 'Fabales',        'Fabaceae',      'Leucaena',    'leucocephala',   NULL),
    ('Plantae', 'Magnoliophyta', 'Magnoliopsida', 'Solanales',      'Solanaceae',    'Capsicum',    'annuum',         NULL);


-- =====================================================================
-- 3. Especies
-- =====================================================================
-- Cada fila enlaza su taxonomía por (genero, especie_taxonomica) con
-- subespecie nula, que es lo que acaba de insertar el bloque 2.
-- origen = 'El Salvador' en las 15. propiedades, importancia_cultural y
-- advertencias quedan en NULL (las tres columnas lo admiten).

INSERT INTO especie (id_taxonomia, nombre_cientifico, descripcion, origen,
                     propiedades, importancia_cultural, advertencias,
                     estado_publicacion, activa,
                     creada_por, validada_por, fecha_validacion,
                     publicada_por, fecha_publicacion)
SELECT tx.id_taxonomia,
       d.nombre_cientifico,
       d.descripcion,
       'El Salvador',
       NULL, NULL, NULL,
       'PUBLICADA',
       true,
       (SELECT id_usuario FROM usuario WHERE nombre_usuario = 'investigador'),
       (SELECT id_usuario FROM usuario WHERE nombre_usuario = 'investigador'),
       LOCALTIMESTAMP,
       (SELECT id_usuario FROM usuario WHERE nombre_usuario = 'admin'),
       LOCALTIMESTAMP
FROM (VALUES
    ('Bactris guineensis', 'Bactris', 'guineensis',
     'Palma espinosa de tierras bajas secas; se aprovecha el cogollo o tallo tierno como vegetal.'),

    ('Bromelia pinguin', 'Bromelia', 'pinguin',
     'Pariente de la piña (piñuela), hojas en roseta espinosas; se consume el brote o tallo tierno.'),

    ('Chamaedorea tepejilote', 'Chamaedorea', 'tepejilote',
     'Palma de sotobosque cuya inflorescencia tierna se come como verdura, de sabor ligeramente amargo.'),

    ('Sinclairia sublobata', 'Sinclairia', 'sublobata',
     'Planta de la familia del girasol; se aprovechan las hojas tiernas.'),

    ('Cnidoscolus aconitifolius', 'Cnidoscolus', 'aconitifolius',
     'Arbusto de hoja muy nutritiva; se consume siempre cocida, nunca cruda.'),

    ('Byrsonima crassifolia', 'Byrsonima', 'crassifolia',
     'Árbol de sabana; fruto pequeño amarillo, dulce-ácido, para consumo fresco, bebidas y conservas.'),

    ('Averrhoa carambola', 'Averrhoa', 'carambola',
     'Fruto de sección estrellada, jugoso y ácido; destaca por su vitamina C.'),

    ('Cymbopogon citratus', 'Cymbopogon', 'citratus',
     'Gramínea aromática con olor a limón; la hoja se usa en infusiones y como condimento.'),

    ('Portulaca oleracea', 'Portulaca', 'oleracea',
     'Hierba suculenta; hojas y tallos tiernos, crudos o cocidos, ricos en minerales.'),

    ('Spondias purpurea', 'Spondias', 'purpurea',
     'Árbol del jocote; se registra la hoja tierna, de sabor ácido, usada en la cocina local.'),

    ('Brosimum alicastrum', 'Brosimum', 'alicastrum',
     'Árbol cuya semilla se come cocida o tostada y molida; tradicional, alta en proteína y fibra.'),

    ('Moringa oleifera', 'Moringa', 'oleifera',
     'Árbol del que se aprovechan hoja, flor y semilla; la hoja es muy rica en proteína, calcio, hierro y vitamina C.'),

    ('Cordia dentata', 'Cordia', 'dentata',
     'Árbol de zonas secas; fruto pequeño, dulce y mucilaginoso, de consumo fresco.'),

    ('Leucaena leucocephala', 'Leucaena', 'leucocephala',
     'Leguminosa arbórea; semilla verde tierna de consumo local; moderar por su contenido de mimosina.'),

    ('Capsicum annuum', 'Capsicum', 'annuum',
     'Chile criollo de fruto rojo alargado ("cola de gallo"), picante, condimento; alto en vitamina C.')
) AS d(nombre_cientifico, genero, especie_taxonomica, descripcion)
JOIN taxonomia tx
  ON tx.genero = d.genero
 AND tx.especie_taxonomica = d.especie_taxonomica
 AND tx.subespecie IS NULL;


-- =====================================================================
-- 4. Nombres comunes
-- =====================================================================
-- Uno por especie, el principal de la bitácora. es_principal = TRUE y
-- region = 'El Salvador' en las 15.

INSERT INTO nombre_comun (id_especie, nombre, region, es_principal, activo)
SELECT e.id_especie, d.nombre, 'El Salvador', true, true
FROM (VALUES
    ('Bactris guineensis',        'Guiscoyol'),
    ('Bromelia pinguin',          'Hijos de piña'),
    ('Chamaedorea tepejilote',    'Pacaya'),
    ('Sinclairia sublobata',      'Papelillo'),
    ('Cnidoscolus aconitifolius', 'Chaya'),
    ('Byrsonima crassifolia',     'Nance'),
    ('Averrhoa carambola',        'Carambola'),
    ('Cymbopogon citratus',       'Zacate limón'),
    ('Portulaca oleracea',        'Verdolaga'),
    ('Spondias purpurea',         'Hoja de jocote'),
    ('Brosimum alicastrum',       'Ojushte'),
    ('Moringa oleifera',          'Moringa'),
    ('Cordia dentata',            'Tigüilote'),
    ('Leucaena leucocephala',     'Leucaena verde'),
    ('Capsicum annuum',           'Espuela de gallo')
) AS d(nombre_cientifico, nombre)
JOIN especie e ON lower(e.nombre_cientifico) = lower(d.nombre_cientifico);


-- =====================================================================
-- 5. Partes comestibles
-- =====================================================================
-- 17 filas: 14 especies con una parte y Moringa con tres (flor, semilla y
-- hoja). Moringa es UNA especie con tres partes, no tres especies.
-- id_parte_planta se resuelve por parte_planta.nombre; los cinco nombres que
-- se usan (Hoja, Flor, Fruto, Semilla, Tallo) ya existen en el catálogo, así
-- que esta migración no crea ninguna parte nueva.

INSERT INTO especie_parte_comestible (id_especie, id_parte_planta, descripcion, advertencias, activa)
SELECT e.id_especie, pp.id_parte_planta, NULL, NULL, true
FROM (VALUES
    ('Bactris guineensis',        'Tallo'),
    ('Bromelia pinguin',          'Tallo'),
    ('Chamaedorea tepejilote',    'Flor'),
    ('Sinclairia sublobata',      'Hoja'),
    ('Cnidoscolus aconitifolius', 'Hoja'),
    ('Byrsonima crassifolia',     'Fruto'),
    ('Averrhoa carambola',        'Fruto'),
    ('Cymbopogon citratus',       'Hoja'),
    ('Portulaca oleracea',        'Hoja'),
    ('Spondias purpurea',         'Hoja'),
    ('Brosimum alicastrum',       'Semilla'),
    ('Moringa oleifera',          'Flor'),
    ('Moringa oleifera',          'Semilla'),
    ('Moringa oleifera',          'Hoja'),
    ('Cordia dentata',            'Fruto'),
    ('Leucaena leucocephala',     'Semilla'),
    ('Capsicum annuum',           'Fruto')
) AS d(nombre_cientifico, parte)
JOIN especie e      ON lower(e.nombre_cientifico) = lower(d.nombre_cientifico)
JOIN parte_planta pp ON pp.nombre = d.parte;


-- =====================================================================
-- 6. Imágenes
-- =====================================================================
-- Una imagen general por especie: id_especie_parte NULL y tipo_imagen
-- PLANTA_COMPLETA (ver nota 3a del encabezado: el enum no tiene un valor
-- "GENERAL"). es_principal = TRUE, una sola por especie, que es lo que admite
-- el índice parcial uk_imagen_principal_especie.
--
-- texto_alternativo es NOT NULL y la bitácora no lo traía: se arma con el
-- nombre común y el científico (ver nota 4). titulo, descripcion y creditos
-- quedan en NULL porque la bitácora no registró la autoría de las fotos.

INSERT INTO imagen_especie (id_especie, id_especie_parte, tipo_imagen, url_imagen,
                            titulo, descripcion, texto_alternativo, creditos,
                            es_principal, activa)
SELECT e.id_especie,
       NULL,
       'PLANTA_COMPLETA',
       d.url_imagen,
       NULL,
       NULL,
       'Fotografía de la planta de ' || d.nombre_comun || ' (' || d.nombre_cientifico || ').',
       NULL,
       true,
       true
FROM (VALUES
    ('Bactris guineensis', 'Guiscoyol',
     'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRlI1NRCjx-YXZr6YtIprdNDf8lJjgT4F18xqcPBWDSbjlIjndzoRzZ5v4&s=10'),

    ('Bromelia pinguin', 'Hijos de piña',
     'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQfGfw5s-3zccKNFXnYMJDYZY8RbNH-r1bTYVGb_TGKxXDousnNtfzvAAqy&s=10'),

    ('Chamaedorea tepejilote', 'Pacaya',
     'https://live.staticflickr.com/4344/36420931256_a17a409892_b.jpg'),

    ('Sinclairia sublobata', 'Papelillo',
     'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTJjxlXt3w3b3BkEHd8SMj6EgYa2FuCDIYOiGitG7s1z9zGs4KjYkYQ32s&s=10'),

    ('Cnidoscolus aconitifolius', 'Chaya',
     'https://www.elmundoforestal.com/wp-content/uploads/2020/09/chaya.jpg'),

    ('Byrsonima crassifolia', 'Nance',
     'https://naturales.ues.edu.sv/subunidadambiental/wp-content/uploads/sites/2/2022/12/Sp2_Nance3.jpg'),

    ('Averrhoa carambola', 'Carambola',
     'https://www.picturethisai.com/image-handle/website_cmsname/image/1080/154140444945547287.jpeg?x-oss-process=image/format,webp'),

    ('Cymbopogon citratus', 'Zacate limón',
     'https://m.media-amazon.com/images/I/61NN5QlP5LL._AC_UF1000,1000_QL80_.jpg'),

    ('Portulaca oleracea', 'Verdolaga',
     'https://upload.wikimedia.org/wikipedia/commons/0/05/Common_Purslane%2C_United_States_imported_from_iNaturalist_photo_541953905.jpg'),

    ('Spondias purpurea', 'Hoja de jocote',
     'https://naturales.ues.edu.sv/subunidadambiental/wp-content/uploads/sites/2/2022/11/Spondias-purpurea-L3.jpg'),

    ('Brosimum alicastrum', 'Ojushte',
     'https://inaturalist-open-data.s3.amazonaws.com/photos/196368709/medium.jpeg'),

    ('Moringa oleifera', 'Moringa',
     'https://www.picturethisai.com/image-handle/website_cmsname/image/1080/153964355581378589.jpeg?x-oss-process=image/format,webp'),

    ('Cordia dentata', 'Tigüilote',
     'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTmrU1z9hoPU5V6R6ntJxgj4tswh-gDKHaYS4rlz9ltnI-L4q5vLU52AC0&s=10'),

    ('Leucaena leucocephala', 'Leucaena verde',
     'https://previews.123rf.com/images/mansum007/mansum0071708/mansum007170800598/85543829-fresh-green-leucaena-glauca-seeds-in-nature-garden.jpg'),

    ('Capsicum annuum', 'Espuela de gallo',
     'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRKbHuZRzrUEPvQJwrkCFS5yNx59wkjtKszzf_HqCb87LqC_Drt2Vxih_Pt&s=10')
) AS d(nombre_cientifico, nombre_comun, url_imagen)
JOIN especie e ON lower(e.nombre_cientifico) = lower(d.nombre_cientifico);


-- =====================================================================
-- 7. Ubicaciones
-- =====================================================================
-- Una ubicación activa por especie. El id_distrito se resuelve por el nombre
-- del distrito DENTRO de su municipio y departamento (los nombres de distrito
-- se repiten entre departamentos, así que el triple JOIN es necesario).
--
-- Las coordenadas son aproximadas: centroide del distrito. La bitácora de
-- campo no traía GPS, por eso fecha_observacion queda en NULL y no se inventa
-- una fecha de recolecta.
--
-- Pacaya y Zacate limón van a Cojutepeque (Cuscatlán Sur, Cuscatlán): ver
-- nota 7 del encabezado. Cuscatlán ya existía, no se crea geografía nueva.

INSERT INTO ubicacion_especie (id_especie, id_distrito, nombre_lugar, latitud, longitud,
                               descripcion, fecha_observacion, activa)
SELECT e.id_especie, di.id_distrito, d.nombre_lugar,
       d.latitud::numeric(9,7), d.longitud::numeric(10,7),
       NULL, NULL, true
FROM (VALUES
    ('Bactris guineensis',        'San Vicente', 'San Vicente Sur', 'San Vicente', 'Hacienda San Francisco, caserío La Laguneta', 13.6417, -88.7842),
    ('Bromelia pinguin',          'San Vicente', 'San Vicente Sur', 'San Vicente', 'Hacienda San Francisco, caserío La Laguneta', 13.6417, -88.7842),
    ('Chamaedorea tepejilote',    'Cuscatlán',   'Cuscatlán Sur',   'Cojutepeque', 'Cuscatlán',                                   13.7167, -88.9333),
    ('Sinclairia sublobata',      'San Vicente', 'San Vicente Sur', 'Guadalupe',   'Distrito de Guadalupe',                       13.5897, -88.8503),
    ('Cnidoscolus aconitifolius', 'San Vicente', 'San Vicente Sur', 'Guadalupe',   'Distrito de Guadalupe',                       13.5897, -88.8503),
    ('Byrsonima crassifolia',     'San Vicente', 'San Vicente Sur', 'Guadalupe',   'Distrito de Guadalupe',                       13.5897, -88.8503),
    ('Averrhoa carambola',        'San Vicente', 'San Vicente Sur', 'Guadalupe',   'Distrito de Guadalupe',                       13.5897, -88.8503),
    ('Cymbopogon citratus',       'Cuscatlán',   'Cuscatlán Sur',   'Cojutepeque', 'Molienda los Gavidia',                        13.7167, -88.9333),
    ('Portulaca oleracea',        'San Vicente', 'San Vicente Sur', 'Guadalupe',   'Distrito de Guadalupe',                       13.5897, -88.8503),
    ('Spondias purpurea',         'San Vicente', 'San Vicente Sur', 'Guadalupe',   'Distrito de Guadalupe',                       13.5897, -88.8503),
    ('Brosimum alicastrum',       'San Vicente', 'San Vicente Sur', 'Tecoluca',    'Comunidad Taura, San Carlos Lempa',           13.3300, -88.7300),
    ('Moringa oleifera',          'San Vicente', 'San Vicente Sur', 'Tecoluca',    'Comunidad Rancho Grande, San Carlos Lempa',   13.3300, -88.7300),
    ('Cordia dentata',            'San Vicente', 'San Vicente Sur', 'Tecoluca',    'Comunidad Taura, San Carlos Lempa',           13.3300, -88.7300),
    ('Leucaena leucocephala',     'San Vicente', 'San Vicente Sur', 'Tecoluca',    'Comunidad Taura, San Carlos Lempa',           13.3300, -88.7300),
    ('Capsicum annuum',           'San Vicente', 'San Vicente Sur', 'Tecoluca',    'Comunidad Taura, San Carlos Lempa',           13.3300, -88.7300)
) AS d(nombre_cientifico, departamento, municipio, distrito, nombre_lugar, latitud, longitud)
JOIN especie e        ON lower(e.nombre_cientifico) = lower(d.nombre_cientifico)
JOIN departamento dep ON dep.nombre = d.departamento
JOIN municipio mu     ON mu.id_departamento = dep.id_departamento AND mu.nombre = d.municipio
JOIN distrito di      ON di.id_municipio = mu.id_municipio        AND di.nombre = d.distrito;


-- =====================================================================
-- 8. Nutrientes usados por el bloque 11
-- =====================================================================
-- Los ocho ya existen en el catálogo (verificado contra la base). El INSERT
-- va igual, protegido por nutriente_nombre_key, para que la migración también
-- corra sobre una base que no los tenga.
--
-- OJO con los nombres reales: son 'Energia' y 'Proteina', SIN tilde, tal como
-- los cargó el seed. Escribirlos con tilde crearía nutrientes duplicados.

INSERT INTO nutriente (nombre, categoria, descripcion, activo)
VALUES
    ('Energia',       'ENERGIA',        NULL, true),
    ('Proteina',      'MACRONUTRIENTE', NULL, true),
    ('Grasa',         'MACRONUTRIENTE', NULL, true),
    ('Carbohidratos', 'MACRONUTRIENTE', NULL, true),
    ('Fibra',         'FIBRA',          NULL, true),
    ('Calcio',        'MINERAL',        NULL, true),
    ('Hierro',        'MINERAL',        NULL, true),
    ('Vitamina C',    'VITAMINA',       NULL, true)
ON CONFLICT (nombre) DO NOTHING;


-- =====================================================================
-- 9. Fuentes
-- =====================================================================
-- `fuente` no tiene ninguna constraint ni índice único (solo la PK), así que
-- un ON CONFLICT ahí no compila: la protección va con WHERE NOT EXISTS sobre
-- lower(titulo), igual que hizo V9.
--
-- tipo_fuente no admite 'CIENTIFICA' (ver nota 3b). anio solo se escribe donde
-- la bitácora lo daba: 2025 para F2, que lo trae en el propio título. Para
-- F1, F3 y F4 queda NULL en vez de deducir un año.
-- F4 no tiene URL única, así que url va NULL y el detalle queda en autor.

INSERT INTO fuente (titulo, autor, anio, tipo_fuente, url, referencia_bibliografica, activa)
SELECT d.titulo, d.autor, d.anio::smallint, d.tipo_fuente, d.url, NULL, true
FROM (VALUES
    ('USDA FoodData Central',
     'U.S. Department of Agriculture',
     NULL, 'SITIO_WEB',
     'https://fdc.nal.usda.gov'),

    ('Nutritional Value, Antinutritional Factors and Protein Quality of Brosimum alicastrum Seeds (2025)',
     'PMC12447510',
     2025, 'ARTICULO',
     'https://pmc.ncbi.nlm.nih.gov/articles/PMC12447510/'),

    ('The ethnobotany of chaya (Cnidoscolus aconitifolius)',
     'Ross-Ibarra & Molina-Cruz, Purdue NewCROP',
     NULL, 'ARTICULO',
     'https://hort.purdue.edu/newcrop/proceedings1996/V3-516.html'),

    ('Composición de hoja de Moringa oleifera (literatura de referencia)',
     'Literatura; por ratificar',
     NULL, 'OTRO',
     NULL)
) AS d(titulo, autor, anio, tipo_fuente, url)
WHERE NOT EXISTS (
    SELECT 1 FROM fuente f WHERE lower(f.titulo) = lower(d.titulo)
);


-- =====================================================================
-- 10. Especie - fuente
-- =====================================================================

INSERT INTO especie_fuente (id_especie, id_fuente, observacion)
SELECT e.id_especie, f.id_fuente, NULL
FROM (VALUES
    ('Averrhoa carambola',        'USDA FoodData Central'),
    ('Byrsonima crassifolia',     'USDA FoodData Central'),
    ('Portulaca oleracea',        'USDA FoodData Central'),
    ('Capsicum annuum',           'USDA FoodData Central'),
    ('Brosimum alicastrum',       'Nutritional Value, Antinutritional Factors and Protein Quality of Brosimum alicastrum Seeds (2025)'),
    ('Cnidoscolus aconitifolius', 'The ethnobotany of chaya (Cnidoscolus aconitifolius)'),
    ('Moringa oleifera',          'Composición de hoja de Moringa oleifera (literatura de referencia)')
) AS d(nombre_cientifico, titulo)
JOIN especie e ON lower(e.nombre_cientifico) = lower(d.nombre_cientifico)
JOIN fuente f  ON lower(f.titulo) = lower(d.titulo);


-- =====================================================================
-- 11. Aporte nutricional
-- =====================================================================
-- Valores por 100 g de la parte indicada, así que porcion_referencia es
-- '100 g de parte comestible' en las 52 filas (la columna es NOT NULL y forma
-- parte de uk_aporte_parte_nutriente_porcion).
--
-- Solo siete pares especie-parte traen datos. Las celdas que la bitácora
-- dejaba en "—" NO generan fila: no se rellenan con cero, porque un cero
-- afirmaría que el nutriente no está presente, que es distinto de no haberlo
-- medido. Por eso salen 52 filas y no 56: falta la fibra de Verdolaga, la
-- energía de Chaya, y la energía y la vitamina C de Ojushte.
--
-- Las demás especies y partes (Guiscoyol, Hijos de piña, Pacaya, Papelillo,
-- Zacate limón, Hoja de jocote, Tigüilote, Leucaena, y la flor y la semilla de
-- Moringa) quedan sin aporte nutricional por ahora, tal como vino la bitácora.
--
-- La unidad sale de `unidad` y no se repite en cada fila: kcal para energía,
-- g para los macronutrientes y la fibra, mg para calcio, hierro y vitamina C.
-- `contexto` lleva la fuente y la observación de cada par especie-parte.

INSERT INTO aporte_nutricional (id_especie_parte, id_nutriente, id_fuente,
                                cantidad, unidad_medida, porcion_referencia, observacion)
WITH unidad(nutriente, unidad_medida) AS (
    VALUES ('Energia',       'kcal'),
           ('Proteina',      'g'),
           ('Grasa',         'g'),
           ('Carbohidratos', 'g'),
           ('Fibra',         'g'),
           ('Calcio',        'mg'),
           ('Hierro',        'mg'),
           ('Vitamina C',    'mg')
),
contexto(nombre_cientifico, parte, titulo_fuente, observacion) AS (
    VALUES
        ('Averrhoa carambola',        'Fruto',   'USDA FoodData Central', NULL),
        ('Byrsonima crassifolia',     'Fruto',   'USDA FoodData Central', NULL),
        ('Portulaca oleracea',        'Hoja',    'USDA FoodData Central', NULL),
        ('Capsicum annuum',           'Fruto',   'USDA FoodData Central', NULL),
        ('Cnidoscolus aconitifolius', 'Hoja',    'The ethnobotany of chaya (Cnidoscolus aconitifolius)', NULL),
        ('Moringa oleifera',          'Hoja',    'Composición de hoja de Moringa oleifera (literatura de referencia)',
         'valor de referencia de literatura, por ratificar'),
        ('Brosimum alicastrum',       'Semilla', 'Nutritional Value, Antinutritional Factors and Protein Quality of Brosimum alicastrum Seeds (2025)',
         'valores en base seca')
),
dato(nombre_cientifico, parte, nutriente, cantidad) AS (
    VALUES
        -- Carambola, fruto (F1)
        ('Averrhoa carambola', 'Fruto', 'Energia',        31),
        ('Averrhoa carambola', 'Fruto', 'Proteina',       1.04),
        ('Averrhoa carambola', 'Fruto', 'Grasa',          0.33),
        ('Averrhoa carambola', 'Fruto', 'Carbohidratos',  6.73),
        ('Averrhoa carambola', 'Fruto', 'Fibra',          2.8),
        ('Averrhoa carambola', 'Fruto', 'Calcio',         3),
        ('Averrhoa carambola', 'Fruto', 'Hierro',         0.08),
        ('Averrhoa carambola', 'Fruto', 'Vitamina C',     34.4),

        -- Nance, fruto (F1)
        ('Byrsonima crassifolia', 'Fruto', 'Energia',       73),
        ('Byrsonima crassifolia', 'Fruto', 'Proteina',      0.66),
        ('Byrsonima crassifolia', 'Fruto', 'Grasa',         1.16),
        ('Byrsonima crassifolia', 'Fruto', 'Carbohidratos', 16.97),
        ('Byrsonima crassifolia', 'Fruto', 'Fibra',         7.5),
        ('Byrsonima crassifolia', 'Fruto', 'Calcio',        46),
        ('Byrsonima crassifolia', 'Fruto', 'Hierro',        0.35),
        ('Byrsonima crassifolia', 'Fruto', 'Vitamina C',    92.5),

        -- Verdolaga, hoja (F1). Sin dato de fibra.
        ('Portulaca oleracea', 'Hoja', 'Energia',       20),
        ('Portulaca oleracea', 'Hoja', 'Proteina',      2.03),
        ('Portulaca oleracea', 'Hoja', 'Grasa',         0.36),
        ('Portulaca oleracea', 'Hoja', 'Carbohidratos', 3.39),
        ('Portulaca oleracea', 'Hoja', 'Calcio',        65),
        ('Portulaca oleracea', 'Hoja', 'Hierro',        1.99),
        ('Portulaca oleracea', 'Hoja', 'Vitamina C',    21),

        -- Espuela de gallo, fruto (F1)
        ('Capsicum annuum', 'Fruto', 'Energia',       40),
        ('Capsicum annuum', 'Fruto', 'Proteina',      1.9),
        ('Capsicum annuum', 'Fruto', 'Grasa',         0.44),
        ('Capsicum annuum', 'Fruto', 'Carbohidratos', 8.8),
        ('Capsicum annuum', 'Fruto', 'Fibra',         1.5),
        ('Capsicum annuum', 'Fruto', 'Calcio',        14),
        ('Capsicum annuum', 'Fruto', 'Hierro',        1.0),
        ('Capsicum annuum', 'Fruto', 'Vitamina C',    144),

        -- Chaya, hoja (F3). Sin dato de energía.
        ('Cnidoscolus aconitifolius', 'Hoja', 'Proteina',      5.7),
        ('Cnidoscolus aconitifolius', 'Hoja', 'Grasa',         0.4),
        ('Cnidoscolus aconitifolius', 'Hoja', 'Carbohidratos', 4.2),
        ('Cnidoscolus aconitifolius', 'Hoja', 'Fibra',         1.9),
        ('Cnidoscolus aconitifolius', 'Hoja', 'Calcio',        199.4),
        ('Cnidoscolus aconitifolius', 'Hoja', 'Hierro',        11.4),
        ('Cnidoscolus aconitifolius', 'Hoja', 'Vitamina C',    164.7),

        -- Moringa, hoja (F4)
        ('Moringa oleifera', 'Hoja', 'Energia',       64),
        ('Moringa oleifera', 'Hoja', 'Proteina',      9.4),
        ('Moringa oleifera', 'Hoja', 'Grasa',         1.4),
        ('Moringa oleifera', 'Hoja', 'Carbohidratos', 8.3),
        ('Moringa oleifera', 'Hoja', 'Fibra',         2.0),
        ('Moringa oleifera', 'Hoja', 'Calcio',        185),
        ('Moringa oleifera', 'Hoja', 'Hierro',        4.0),
        ('Moringa oleifera', 'Hoja', 'Vitamina C',    51.7),

        -- Ojushte, semilla (F2). Sin dato de energía ni de vitamina C.
        ('Brosimum alicastrum', 'Semilla', 'Proteina',      10.69),
        ('Brosimum alicastrum', 'Semilla', 'Grasa',         1.35),
        ('Brosimum alicastrum', 'Semilla', 'Carbohidratos', 60.67),
        ('Brosimum alicastrum', 'Semilla', 'Fibra',         15.49),
        ('Brosimum alicastrum', 'Semilla', 'Calcio',        108.99),
        ('Brosimum alicastrum', 'Semilla', 'Hierro',        1.86)
)
SELECT epc.id_especie_parte,
       n.id_nutriente,
       f.id_fuente,
       dato.cantidad::numeric(12,4),
       u.unidad_medida,
       '100 g de parte comestible',
       c.observacion
FROM dato
JOIN unidad u   ON u.nutriente = dato.nutriente
JOIN contexto c ON c.nombre_cientifico = dato.nombre_cientifico AND c.parte = dato.parte
JOIN especie e  ON lower(e.nombre_cientifico) = lower(dato.nombre_cientifico)
JOIN parte_planta pp ON pp.nombre = dato.parte
JOIN especie_parte_comestible epc
     ON epc.id_especie = e.id_especie AND epc.id_parte_planta = pp.id_parte_planta
JOIN nutriente n ON n.nombre = dato.nutriente
JOIN fuente f    ON lower(f.titulo) = lower(c.titulo_fuente);


-- =====================================================================
-- 12. Verificación
-- =====================================================================
-- Red de seguridad. Todos los INSERT de arriba resuelven sus FK con JOIN, y
-- un JOIN que no encuentra su fila no falla: simplemente no inserta. Sin esta
-- comprobación, un nombre de distrito, de parte o de nutriente que no calzara
-- dejaría el catálogo cargado a medias y la migración marcada como exitosa.
--
-- Con el RAISE EXCEPTION, Flyway revierte la transacción completa y el
-- mensaje dice qué bloque quedó corto.

DO $$
DECLARE
    v_especies   integer;
    v_taxonomias integer;
    v_nombres    integer;
    v_partes     integer;
    v_imagenes   integer;
    v_ubicaciones integer;
    v_fuentes    integer;
    v_aportes    integer;
BEGIN
    SELECT count(*) INTO v_especies    FROM especie;
    SELECT count(*) INTO v_taxonomias  FROM taxonomia;
    SELECT count(*) INTO v_nombres     FROM nombre_comun;
    SELECT count(*) INTO v_partes      FROM especie_parte_comestible;
    SELECT count(*) INTO v_imagenes    FROM imagen_especie;
    SELECT count(*) INTO v_ubicaciones FROM ubicacion_especie;
    SELECT count(*) INTO v_fuentes     FROM especie_fuente;
    SELECT count(*) INTO v_aportes     FROM aporte_nutricional;

    IF v_especies <> 15 THEN
        RAISE EXCEPTION 'V14: se esperaban 15 especies y hay %', v_especies;
    END IF;
    IF v_taxonomias <> 15 THEN
        RAISE EXCEPTION 'V14: se esperaban 15 taxonomias y hay %', v_taxonomias;
    END IF;
    IF v_nombres <> 15 THEN
        RAISE EXCEPTION 'V14: se esperaban 15 nombres comunes y hay %', v_nombres;
    END IF;
    IF v_partes <> 17 THEN
        RAISE EXCEPTION 'V14: se esperaban 17 partes comestibles (14 especies + 3 de Moringa) y hay %', v_partes;
    END IF;
    IF v_imagenes <> 15 THEN
        RAISE EXCEPTION 'V14: se esperaban 15 imagenes y hay %', v_imagenes;
    END IF;
    IF v_ubicaciones <> 15 THEN
        RAISE EXCEPTION 'V14: se esperaban 15 ubicaciones y hay % (revisar los nombres de distrito)', v_ubicaciones;
    END IF;
    IF v_fuentes <> 7 THEN
        RAISE EXCEPTION 'V14: se esperaban 7 vinculos especie-fuente y hay %', v_fuentes;
    END IF;
    IF v_aportes <> 52 THEN
        RAISE EXCEPTION 'V14: se esperaban 52 aportes nutricionales y hay %', v_aportes;
    END IF;
END $$;
