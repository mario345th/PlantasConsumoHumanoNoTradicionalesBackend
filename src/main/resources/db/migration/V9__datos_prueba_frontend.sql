-- V9__datos_prueba_frontend.sql
--
-- Datos de prueba para levantar el frontend en local con contenido visible.
-- NO son datos reales del sistema: las especies y su taxonomía son correctas,
-- pero las descripciones, las fuentes bibliográficas y los usuarios son
-- material de prueba redactado para esta carga.
--
-- Alcance: catálogos (habitat, fuente, nutriente, parte_planta, beneficio),
-- especie con su taxonomia y sus nombres comunes, y el módulo de seguridad
-- (usuario, rol, permiso, usuario_rol, rol_permiso). Las tablas de asociación
-- (especie_parte_comestible, aporte_nutricional, parte_beneficio,
-- especie_fuente, especie_habitat) quedan fuera a propósito.
--
-- Decisiones que aplican a todo el script:
--
-- 1. Ninguna PK se escribe literal. Todas son GENERATED ALWAYS AS IDENTITY y
--    las llaves foráneas se resuelven por su valor natural (nombre, código,
--    nombre de usuario, nombre científico), igual que V4/V6/V7 con los
--    municipios y distritos.
--
-- 2. Idempotencia. La base de desarrollo ya trae datos de migraciones
--    anteriores y de las pruebas de QA, así que cada INSERT se protege:
--      - ON CONFLICT DO NOTHING donde hay una restricción única;
--      - WHERE NOT EXISTS donde NO la hay. El caso es `fuente`: esa tabla no
--        tiene ninguna constraint ni índice único (se verificó contra la base
--        real), así que un ON CONFLICT ahí no compila.
--    Nada de lo que ya existe se borra ni se modifica.
--
-- 3. Columnas de fecha que llena la base (fecha_registro, fecha_actualizacion
--    de especie y usuario, anotadas @Generated en las entidades) no se
--    insertan. usuario_rol.fecha_asignacion tampoco: tiene DEFAULT.
--
-- 4. El género de la columna de activo cambia según la tabla. Verificado
--    contra el DDL real, una por una:
--      activo  -> habitat, nutriente, parte_planta, beneficio, usuario, rol,
--                 permiso, nombre_comun
--      activa  -> fuente, especie
--      (taxonomia, usuario_rol y rol_permiso no tienen esa columna)
--
-- 5. Los valores de los enums salen de los CHECK de la base:
--      especie.estado_publicacion -> BORRADOR, EN_REVISION, PUBLICADA, RECHAZADA
--      fuente.tipo_fuente         -> LIBRO, ARTICULO, SITIO_WEB, ENTREVISTA,
--                                    TESIS, INFORME, CONOCIMIENTO_ANCESTRAL, OTRO
--      nutriente.categoria        -> MACRONUTRIENTE, VITAMINA, MINERAL,
--                                    ENERGIA, FIBRA, OTRO
--      beneficio.tipo_beneficio   -> NUTRICIONAL, FUNCIONAL, OTRO
--                                    (no existe MEDICINAL)


-- =====================================================================
-- 1. Catálogo de hábitats
-- =====================================================================
-- habitat.nombre tiene UNIQUE (habitat_nombre_key), así que el ON CONFLICT
-- va sobre la columna. Los nombres son distintos de los que ya cargó QA.

INSERT INTO habitat (nombre, descripcion, activo)
VALUES
    ('Bosque húmedo tropical',      'Bosque de clima cálido y lluvioso, con alta humedad relativa durante casi todo el año.', true),
    ('Bosque seco tropical',        'Bosque con estación seca marcada; buena parte de los árboles pierde la hoja.',           true),
    ('Zona cafetalera',             'Cafetal bajo sombra; el estrato arbóreo alberga especies útiles asociadas al cultivo.',  true),
    ('Ribera de río',               'Franja de vegetación a la orilla de un cauce, con suelo húmedo todo el año.',            true),
    ('Matorral',                    'Vegetación baja y arbustiva, frecuente en terrenos degradados o en recuperación.',       true),
    ('Traspatio o huerto familiar', 'Terreno contiguo a la vivienda donde se cultivan especies de consumo doméstico.',        true),
    ('Bosque de galería',           'Vegetación arbórea que acompaña el curso de un río dentro de una zona abierta.',         true)
ON CONFLICT (nombre) DO NOTHING;


-- =====================================================================
-- 2. Catálogo de fuentes
-- =====================================================================
-- `fuente` NO tiene restricción única (ni en titulo ni en ninguna otra
-- columna), por eso aquí se usa WHERE NOT EXISTS y no ON CONFLICT.
--
-- Las referencias son de prueba: están redactadas para que el frontend
-- muestre fichas con autor, año y tipo, no para citarse en un documento.
-- Por eso referencia_bibliografica lo dice explícitamente.

INSERT INTO fuente (titulo, autor, anio, tipo_fuente, url, referencia_bibliografica, activa)
SELECT f.titulo, f.autor, f.anio, f.tipo_fuente, f.url, f.referencia_bibliografica, true
FROM (VALUES
          ('Catálogo de plantas útiles del Herbario Nacional de El Salvador (MHES)',
           'Museo de Historia Natural de El Salvador',
           2019::smallint,
           'INFORME',
           'https://www.cultura.gob.sv/museo-de-historia-natural-de-el-salvador/',
           'Dato de prueba: referencia ilustrativa, no corresponde a una publicación verificada.'),

          ('Hortalizas tradicionales y plantas de consumo no convencional en Centroamérica',
           'Organización de las Naciones Unidas para la Alimentación y la Agricultura (FAO)',
           2018::smallint,
           'INFORME',
           'https://www.fao.org/',
           'Dato de prueba: referencia ilustrativa, no corresponde a una publicación verificada.'),

          ('Caracterización nutricional de hortalizas nativas de consumo tradicional en El Salvador',
           'Facultad de Ciencias Agronómicas, Universidad de El Salvador',
           2021::smallint,
           'TESIS',
           NULL,
           'Dato de prueba: referencia ilustrativa, no corresponde a una tesis existente.'),

          ('Entrevista a productores de loroco y chipilín de San Vicente Sur',
           'Equipo de investigación UES-FMP',
           2026::smallint,
           'ENTREVISTA',
           NULL,
           'Dato de prueba: registro de conocimiento tradicional simulado para desarrollo.'),

          ('Guía de plantas del Jardín Botánico La Laguna',
           'Jardín Botánico La Laguna',
           2017::smallint,
           'LIBRO',
           'https://jardinbotanico.org.sv/',
           'Dato de prueba: referencia ilustrativa, no corresponde a una publicación verificada.')
     ) AS f(titulo, autor, anio, tipo_fuente, url, referencia_bibliografica)
WHERE NOT EXISTS (
    SELECT 1 FROM fuente x WHERE lower(x.titulo) = lower(f.titulo)
);


-- =====================================================================
-- 3. Catálogo de nutrientes
-- =====================================================================
-- Los nombres se escriben SIN tilde a propósito: la tabla ya trae
-- 'Energia', 'Proteina', etc. Escribir 'Proteína' no chocaría con el UNIQUE
-- (nutriente_nombre_key compara el texto exacto) y dejaría dos filas para el
-- mismo nutriente en el combo del frontend. Los que ya existen los descarta
-- el ON CONFLICT.

INSERT INTO nutriente (nombre, categoria, descripcion, activo)
VALUES
    ('Proteina',     'MACRONUTRIENTE', 'Aporta los aminoácidos necesarios para la formación y reparación de tejidos.', true),
    ('Fibra',        'FIBRA',          'Fracción no digerible que favorece el tránsito intestinal.',                   true),
    ('Hierro',       'MINERAL',        'Participa en el transporte de oxígeno en la sangre.',                          true),
    ('Calcio',       'MINERAL',        'Mineral estructural de huesos y dientes.',                                     true),
    ('Fosforo',      'MINERAL',        'Acompaña al calcio en la mineralización ósea y en el metabolismo energético.', true),
    ('Potasio',      'MINERAL',        'Electrolito que interviene en la función muscular y nerviosa.',                true),
    ('Magnesio',     'MINERAL',        'Cofactor de numerosas reacciones enzimáticas.',                                true),
    ('Zinc',         'MINERAL',        'Interviene en la respuesta inmunitaria y en la cicatrización.',                 true),
    ('Vitamina A',   'VITAMINA',       'Vitamina liposoluble asociada a la visión y a la salud de la piel.',           true),
    ('Vitamina C',   'VITAMINA',       'Vitamina hidrosoluble con acción antioxidante; favorece la absorción de hierro.', true),
    ('Acido folico', 'VITAMINA',       'Vitamina del complejo B relevante durante el embarazo.',                       true)
ON CONFLICT (nombre) DO NOTHING;


-- =====================================================================
-- 4. Catálogo de partes de planta
-- =====================================================================
-- Misma convención sin tilde que el bloque anterior: la tabla ya trae 'Raiz'
-- y 'Tuberculo'. 'Hoja', 'Flor', 'Fruto', 'Semilla', 'Tallo', 'Raiz' y
-- 'Brote' ya existen y los filtra el ON CONFLICT; se dejan listados para que
-- el script también sirva sobre una base limpia.

INSERT INTO parte_planta (nombre, descripcion, activo)
VALUES
    ('Hoja',          'Lámina foliar, tierna o madura.',                                  true),
    ('Flor',          'Flor abierta.',                                                    true),
    ('Fruto',         'Fruto maduro o en sazón.',                                         true),
    ('Semilla',       'Semilla, con o sin cubierta.',                                     true),
    ('Tallo',         'Tallo tierno o leñoso.',                                           true),
    ('Raiz',          'Raíz principal o secundaria.',                                     true),
    ('Brote',         'Brote tierno o retoño.',                                           true),
    ('Inflorescencia','Conjunto de flores agrupadas sobre un mismo eje.',                 true),
    ('Boton floral',  'Flor todavía cerrada, antes de la antesis.',                       true),
    ('Vaina',         'Fruto seco y alargado propio de las leguminosas.',                 true)
ON CONFLICT (nombre) DO NOTHING;


-- =====================================================================
-- 5. Catálogo de beneficios
-- =====================================================================
-- tipo_beneficio solo admite NUTRICIONAL, FUNCIONAL u OTRO (ck_beneficio_tipo,
-- creado en V2). No existe MEDICINAL: los beneficios de esa naturaleza quedan
-- fuera del alcance vigente y por eso no se cargan.
--
-- El único índice único es uk_beneficio_nombre_lower, sobre lower(nombre), que
-- no es una columna; el ON CONFLICT sin destino cubre cualquier índice único
-- de la tabla y evita tener que nombrarlo.

INSERT INTO beneficio (nombre, tipo_beneficio, descripcion, activo)
VALUES
    ('Alimenticio',                      'NUTRICIONAL', 'Se consume como alimento y aporta energía a la dieta diaria.',             true),
    ('Nutricional',                      'NUTRICIONAL', 'Aporta nutrientes relevantes dentro de la dieta.',                         true),
    ('Cultural',                         'OTRO',        'Forma parte de la gastronomía o de las tradiciones de la comunidad.',      true),
    ('Economico',                        'OTRO',        'Genera ingresos para quienes lo cultivan o lo comercializan.',             true),
    ('Ornamental',                       'OTRO',        'Se cultiva también por su valor decorativo.',                              true),
    ('Aporte de fibra',                  'FUNCIONAL',   'Contribuye al aporte de fibra dietética.',                                 true),
    ('Fuente de antioxidantes',          'FUNCIONAL',   'Contiene compuestos con capacidad antioxidante.',                          true),
    ('Apoyo a la seguridad alimentaria', 'OTRO',        'Disponible localmente y de bajo costo para los hogares que lo aprovechan.', true)
ON CONFLICT DO NOTHING;


-- =====================================================================
-- 6. Seguridad: permisos
-- =====================================================================
-- El juego CRUD por módulo. Los catorce ya están en la base de desarrollo;
-- se listan igual para que el script levante una base limpia por sí solo.
-- permiso.codigo tiene UNIQUE (permiso_codigo_key).

INSERT INTO permiso (codigo, nombre, descripcion, activo)
VALUES
    ('ESPECIE_CONSULTAR',     'Consultar especies',                          'Ver el catálogo de especies y sus fichas.',             true),
    ('ESPECIE_CREAR',         'Crear especies',                              'Registrar fichas nuevas de especie.',                   true),
    ('ESPECIE_EDITAR',        'Editar especies',                             'Modificar fichas de especie existentes.',               true),
    ('ESPECIE_VALIDAR',       'Validar especies',                            'Revisar y validar el contenido de una ficha.',          true),
    ('ESPECIE_PUBLICAR',      'Publicar especies',                           'Pasar una ficha validada al catálogo público.',         true),
    ('PARTE_GESTIONAR',       'Gestionar partes comestibles',                'Administrar las partes comestibles de una especie.',    true),
    ('PREPARACION_GESTIONAR', 'Gestionar formas de consumo y preparaciones', 'Administrar preparaciones y formas de consumo.',        true),
    ('NUTRICION_GESTIONAR',   'Gestionar aportes nutricionales',             'Administrar los aportes nutricionales registrados.',    true),
    ('COSECHA_GESTIONAR',     'Gestionar epocas de cosecha',                 'Administrar las épocas de cosecha.',                    true),
    ('FUENTE_GESTIONAR',      'Gestionar fuentes de informacion',            'Administrar las fuentes bibliográficas.',               true),
    ('MULTIMEDIA_GESTIONAR',  'Gestionar imagenes y videos',                 'Administrar el material multimedia del catálogo.',      true),
    ('UBICACION_GESTIONAR',   'Gestionar ubicaciones',                       'Administrar las ubicaciones georreferenciadas.',        true),
    ('CATALOGO_GESTIONAR',    'Gestionar catalogos',                         'Administrar los catálogos auxiliares del sistema.',     true),
    ('USUARIO_GESTIONAR',     'Gestionar usuarios y roles',                  'Administrar usuarios, roles y sus asignaciones.',       true)
ON CONFLICT (codigo) DO NOTHING;


-- =====================================================================
-- 7. Seguridad: roles
-- =====================================================================
-- ADMINISTRADOR e INVESTIGADOR ya existen en la base de desarrollo; EDITOR y
-- CONSULTOR son nuevos. rol.nombre tiene UNIQUE (rol_nombre_key).

INSERT INTO rol (nombre, descripcion, activo)
VALUES
    ('ADMINISTRADOR', 'Acceso total al sistema, incluido el módulo de seguridad.', true),
    ('INVESTIGADOR',  'Registra y edita el contenido científico del catálogo.',    true),
    ('EDITOR',        'Revisa, valida y publica las fichas de especie.',           true),
    ('CONSULTOR',     'Solo consulta el catálogo.',                                true)
ON CONFLICT (nombre) DO NOTHING;


-- =====================================================================
-- 8. Seguridad: usuarios de demostración
-- =====================================================================
-- Usuarios para que el frontend pueda iniciar sesión en local.
--
--   admin.demo         / Demo2026#
--   investigador.demo  / Investiga2026#
--
-- clave_hash es el hash BCrypt de esas claves, generado con el mismo
-- BCryptPasswordEncoder que usa PasswordEncoderConfig (fuerza 10, prefijo
-- $2a$) y verificado con matches(). Ambas claves miden 9 y 14 bytes en UTF-8,
-- muy por debajo del límite de 72 bytes que trunca BCrypt.
--
-- Son credenciales de desarrollo y están en el repositorio: no deben existir
-- en ningún ambiente que no sea local.
--
-- `usuario` no tiene UNIQUE de columna sino dos índices únicos de expresión
-- (uk_usuario_correo_lower y uk_usuario_nombre_lower). Un ON CONFLICT con
-- destino solo podría apuntar a uno de los dos; sin destino cubre ambos.

INSERT INTO usuario (nombres, apellidos, correo, nombre_usuario, clave_hash, activo)
VALUES
    ('Administrador', 'Demo', 'admin.demo@prueba.local', 'admin.demo',
     '$2a$10$qRCHSrFo5c97vAvGC8od7eXbrbJ3EWYde5WVwvcNphhSGbqmNIS.i', true),
    ('Investigadora', 'Demo', 'investigador.demo@prueba.local', 'investigador.demo',
     '$2a$10$lnurr179o05bonhw8aNE2egW8z7iL2JGdexm5.oJn.3SlVhE8Lbam', true)
ON CONFLICT DO NOTHING;


-- =====================================================================
-- 9. Seguridad: usuario_rol
-- =====================================================================
-- @EmbeddedId (UsuarioRolId): la PK es el par de llaves foráneas, las dos se
-- resuelven por su valor natural. fecha_asignacion la pone el DEFAULT.

INSERT INTO usuario_rol (id_usuario, id_rol)
SELECT u.id_usuario, r.id_rol
FROM (VALUES
          ('admin.demo',        'ADMINISTRADOR'),
          ('investigador.demo', 'INVESTIGADOR')
     ) AS ur(nombre_usuario, rol)
         JOIN usuario u ON u.nombre_usuario = ur.nombre_usuario
         JOIN rol r ON r.nombre = ur.rol
ON CONFLICT (id_usuario, id_rol) DO NOTHING;


-- =====================================================================
-- 10. Seguridad: rol_permiso
-- =====================================================================
-- @EmbeddedId (RolPermisoId), mismo criterio que usuario_rol.
--
-- Los permisos de ADMINISTRADOR e INVESTIGADOR se listan completos aunque la
-- base de desarrollo ya los tenga, para que el script funcione sobre una base
-- limpia. Los permisos QA_* existentes no se tocan.

INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso
FROM (VALUES
          -- ADMINISTRADOR: todo el catálogo más el módulo de seguridad.
          ('ADMINISTRADOR', 'ESPECIE_CONSULTAR'),
          ('ADMINISTRADOR', 'ESPECIE_CREAR'),
          ('ADMINISTRADOR', 'ESPECIE_EDITAR'),
          ('ADMINISTRADOR', 'ESPECIE_VALIDAR'),
          ('ADMINISTRADOR', 'ESPECIE_PUBLICAR'),
          ('ADMINISTRADOR', 'PARTE_GESTIONAR'),
          ('ADMINISTRADOR', 'PREPARACION_GESTIONAR'),
          ('ADMINISTRADOR', 'NUTRICION_GESTIONAR'),
          ('ADMINISTRADOR', 'COSECHA_GESTIONAR'),
          ('ADMINISTRADOR', 'FUENTE_GESTIONAR'),
          ('ADMINISTRADOR', 'MULTIMEDIA_GESTIONAR'),
          ('ADMINISTRADOR', 'UBICACION_GESTIONAR'),
          ('ADMINISTRADOR', 'CATALOGO_GESTIONAR'),
          ('ADMINISTRADOR', 'USUARIO_GESTIONAR'),

          -- INVESTIGADOR: registra contenido científico, no gestiona usuarios
          -- ni publica.
          ('INVESTIGADOR',  'ESPECIE_CONSULTAR'),
          ('INVESTIGADOR',  'ESPECIE_CREAR'),
          ('INVESTIGADOR',  'ESPECIE_EDITAR'),
          ('INVESTIGADOR',  'ESPECIE_VALIDAR'),
          ('INVESTIGADOR',  'PARTE_GESTIONAR'),
          ('INVESTIGADOR',  'PREPARACION_GESTIONAR'),
          ('INVESTIGADOR',  'NUTRICION_GESTIONAR'),
          ('INVESTIGADOR',  'COSECHA_GESTIONAR'),
          ('INVESTIGADOR',  'FUENTE_GESTIONAR'),
          ('INVESTIGADOR',  'MULTIMEDIA_GESTIONAR'),
          ('INVESTIGADOR',  'UBICACION_GESTIONAR'),

          -- EDITOR: cierra el flujo editorial.
          ('EDITOR',        'ESPECIE_CONSULTAR'),
          ('EDITOR',        'ESPECIE_EDITAR'),
          ('EDITOR',        'ESPECIE_VALIDAR'),
          ('EDITOR',        'ESPECIE_PUBLICAR'),
          ('EDITOR',        'MULTIMEDIA_GESTIONAR'),

          -- CONSULTOR: solo lectura.
          ('CONSULTOR',     'ESPECIE_CONSULTAR')
     ) AS rp(rol, permiso)
         JOIN rol r ON r.nombre = rp.rol
         JOIN permiso p ON p.codigo = rp.permiso
ON CONFLICT (id_rol, id_permiso) DO NOTHING;


-- =====================================================================
-- 11. Taxonomía de las ocho especies
-- =====================================================================
-- reino no se escribe: la columna tiene DEFAULT 'Plantae' y el CHECK
-- ck_taxonomia_reino no admite otra cosa. division y clase siguen la
-- convención que ya usan las filas cargadas (Magnoliophyta, y
-- Magnoliopsida / Liliopsida para dicotiledóneas y monocotiledóneas).
--
-- uk_taxonomia es UNIQUE NULLS NOT DISTINCT (reino, genero, especie_taxonomica,
-- subespecie): dos clasificaciones iguales con subespecie nula chocan entre sí.
--
-- El WHERE NOT EXISTS evita dejar una taxonomía huérfana: si la especie ya
-- está en la base (en desarrollo ya existen 'Inga paterno' y
-- 'Yucca guatemalensis'), no se inserta su clasificación, porque el INSERT de
-- especie del bloque siguiente tampoco la va a usar.

INSERT INTO taxonomia (division, clase, orden_taxonomico, familia, genero, especie_taxonomica)
SELECT t.division, t.clase, t.orden_taxonomico, t.familia, t.genero, t.especie_taxonomica
FROM (VALUES
          ('Magnoliophyta', 'Magnoliopsida', 'Gentianales',   'Apocynaceae',   'Fernaldia',   'pandurata',     'Fernaldia pandurata'),
          ('Magnoliophyta', 'Magnoliopsida', 'Fabales',       'Fabaceae',      'Crotalaria',  'longirostrata', 'Crotalaria longirostrata'),
          ('Magnoliophyta', 'Magnoliopsida', 'Solanales',     'Solanaceae',    'Solanum',     'americanum',    'Solanum americanum'),
          ('Magnoliophyta', 'Liliopsida',    'Asparagales',   'Asparagaceae',  'Yucca',       'guatemalensis', 'Yucca guatemalensis'),
          ('Magnoliophyta', 'Liliopsida',    'Arecales',      'Arecaceae',     'Chamaedorea', 'tepejilote',    'Chamaedorea tepejilote'),
          ('Magnoliophyta', 'Magnoliopsida', 'Malpighiales',  'Malpighiaceae', 'Byrsonima',   'crassifolia',   'Byrsonima crassifolia'),
          ('Magnoliophyta', 'Magnoliopsida', 'Sapindales',    'Anacardiaceae', 'Spondias',    'purpurea',      'Spondias purpurea'),
          ('Magnoliophyta', 'Magnoliopsida', 'Fabales',       'Fabaceae',      'Inga',        'paterno',       'Inga paterno')
     ) AS t(division, clase, orden_taxonomico, familia, genero, especie_taxonomica, nombre_cientifico)
WHERE NOT EXISTS (
    SELECT 1 FROM especie e WHERE lower(e.nombre_cientifico) = lower(t.nombre_cientifico)
)
ON CONFLICT ON CONSTRAINT uk_taxonomia DO NOTHING;


-- =====================================================================
-- 12. Especies
-- =====================================================================
-- Ocho plantas salvadoreñas de consumo no tradicional. Los estados se
-- reparten para poder probar las distintas vistas del frontend:
-- cinco PUBLICADA (las que ve el catálogo público), dos EN_REVISION y una
-- BORRADOR.
--
-- ck_especie_publicacion exige que una especie PUBLICADA traiga validada_por,
-- publicada_por y fecha_publicacion; ck_especie_validacion exige que si hay
-- validada_por haya fecha_validacion. Por eso los cuatro campos viajan juntos
-- y el CASE los deja en NULL para BORRADOR y EN_REVISION.
--
-- creada_por apunta siempre a investigador.demo y la validación/publicación a
-- admin.demo (ambos del bloque 8). Si alguno faltara, el subselect devolvería
-- NULL y el NOT NULL de creada_por haría fallar la migración en vez de
-- insertar datos a medias.
--
-- La taxonomía se enlaza por (genero, especie_taxonomica) con subespecie nula,
-- que es justo lo que insertó el bloque anterior. El segundo NOT EXISTS
-- protege especie_id_taxonomia_key: la relación especie-taxonomia es uno a
-- uno, así que no se reutiliza una clasificación que ya tenga especie.
--
-- fecha_registro y fecha_actualizacion no se insertan: las pone la base.

INSERT INTO especie (id_taxonomia, nombre_cientifico, descripcion, origen, propiedades,
                     importancia_cultural, advertencias, estado_publicacion, activa,
                     creada_por, validada_por, fecha_validacion, publicada_por, fecha_publicacion)
SELECT tx.id_taxonomia,
       e.nombre_cientifico,
       e.descripcion,
       e.origen,
       e.propiedades,
       e.importancia_cultural,
       e.advertencias,
       e.estado_publicacion,
       true,
       (SELECT id_usuario FROM usuario WHERE nombre_usuario = 'investigador.demo'),
       CASE WHEN e.estado_publicacion = 'PUBLICADA'
                THEN (SELECT id_usuario FROM usuario WHERE nombre_usuario = 'admin.demo') END,
       CASE WHEN e.estado_publicacion = 'PUBLICADA' THEN e.fecha_validacion END,
       CASE WHEN e.estado_publicacion = 'PUBLICADA'
                THEN (SELECT id_usuario FROM usuario WHERE nombre_usuario = 'admin.demo') END,
       CASE WHEN e.estado_publicacion = 'PUBLICADA' THEN e.fecha_publicacion END
FROM (VALUES
          ('Fernaldia pandurata', 'Fernaldia', 'pandurata',
           'Enredadera perenne de tallos delgados y flores blancas agrupadas, cuyos botones florales y flores se consumen como hortaliza. Crece silvestre y cultivada en cercos vivos y huertos familiares de buena parte del país.',
           'Nativa de Mesoamérica, de México a Costa Rica.',
           'Aporta fibra, calcio y vitamina C con un bajo aporte calórico.',
           'Es uno de los ingredientes más representativos de la cocina salvadoreña: se usa en pupusas, tamales, sopas y huevos revueltos.',
           'Se consume cocido. El látex que sueltan los tallos al cortarlos puede irritar la piel sensible.',
           'PUBLICADA', TIMESTAMP '2026-09-10 09:15:00', TIMESTAMP '2026-09-12 11:00:00'),

          ('Crotalaria longirostrata', 'Crotalaria', 'longirostrata',
           'Arbusto leguminoso de hasta dos metros, de hojas trifoliadas y flores amarillas, del que se aprovechan las hojas tiernas. Se siembra en traspatios y en los bordes de las parcelas de maíz.',
           'Nativa del sur de México y de Centroamérica.',
           'Hoja con buen contenido de hierro, calcio y proteína vegetal.',
           'Acompaña arroz, sopas y tamales de chipilín; es de las hortalizas de hoja más consumidas en el oriente del país.',
           'Solo se consumen las hojas tiernas cocidas. Las semillas y las vainas maduras no se comen: el género Crotalaria contiene alcaloides pirrolizidínicos.',
           'PUBLICADA', TIMESTAMP '2026-09-10 10:40:00', TIMESTAMP '2026-09-12 11:05:00'),

          ('Byrsonima crassifolia', 'Byrsonima', 'crassifolia',
           'Árbol pequeño de corteza agrietada y hojas coriáceas que produce frutos amarillos y aromáticos del tamaño de una canica. Resiste bien la sequía y los suelos pobres.',
           'Nativa desde México hasta el norte de Sudamérica.',
           'Fruto rico en vitamina C y fibra.',
           'Se come fresco, en refresco, en dulce o curtido en licor; es común encontrarlo en ventas a la orilla de la carretera.',
           NULL,
           'PUBLICADA', TIMESTAMP '2026-09-18 08:30:00', TIMESTAMP '2026-09-21 16:20:00'),

          ('Spondias purpurea', 'Spondias', 'purpurea',
           'Árbol caducifolio de madera blanda y ramas quebradizas que da frutos pequeños de color rojo o morado al madurar. Se propaga con facilidad por estaca, y por eso es frecuente verlo formando cercos vivos.',
           'Nativa de Mesoamérica.',
           'Fruto con aporte de vitamina C, vitamina A y potasio.',
           'Se consume verde con sal y alguashte o maduro en dulce; la cosecha marca una temporada reconocible en los mercados.',
           'El hueso del fruto no se consume.',
           'PUBLICADA', TIMESTAMP '2026-09-18 09:10:00', TIMESTAMP '2026-09-21 16:25:00'),

          ('Inga paterno', 'Inga', 'paterno',
           'Árbol leguminoso de copa amplia que produce vainas largas y aplanadas. Dentro de la vaina, cada semilla viene envuelta en un arilo blanco, dulce y algodonoso, que es la parte que se come.',
           'Nativa de Centroamérica.',
           'El arilo aporta azúcares de rápida absorción y fibra.',
           'Se usa como árbol de sombra en los cafetales y su vaina se vende por unidad en mercados y paradas de bus.',
           'Solo se consume el arilo blanco. La semilla no se come cruda.',
           'PUBLICADA', TIMESTAMP '2026-09-18 09:45:00', TIMESTAMP '2026-09-21 16:30:00'),

          ('Solanum americanum', 'Solanum', 'americanum',
           'Hierba anual de hasta un metro, de flores blancas pequeñas y frutos redondos que maduran de verde a negro. Aparece de forma espontánea en terrenos removidos, huertos y orillas de camino.',
           'De distribución amplia en América; se comporta como arvense en zonas cultivadas.',
           'Hoja con aporte notable de hierro, calcio y vitamina A.',
           'Las hojas cocidas se comen en sopas y guisos; es un recurso alimentario de recolección al alcance de cualquier hogar.',
           'Los frutos verdes y las hojas crudas contienen solanina. Las hojas se hierven y se descarta el agua de cocción antes de consumirlas.',
           'EN_REVISION', NULL::timestamp, NULL::timestamp),

          ('Chamaedorea tepejilote', 'Chamaedorea', 'tepejilote',
           'Palma de sotobosque, de tallo delgado y anillado, que alcanza unos cinco metros. Se aprovecha su inflorescencia masculina todavía cerrada, parecida a una mazorca pequeña.',
           'Nativa de los bosques húmedos de Mesoamérica.',
           'Aporta fibra, fósforo y vitaminas del complejo B.',
           'Se come envuelta en huevo, en escabeche o asada; en algunas zonas del país se cosecha del bosque y se vende por manojo.',
           'La inflorescencia abierta se vuelve muy amarga: se cosecha mientras sigue envuelta en su espata.',
           'EN_REVISION', NULL::timestamp, NULL::timestamp),

          ('Yucca guatemalensis', 'Yucca', 'guatemalensis',
           'Arbusto o arbolito de tallo grueso y hojas rígidas en roseta, que produce panículas de flores blancas y carnosas. Se siembra como cerco vivo y como planta ornamental.',
           'Nativa de Centroamérica.',
           'La flor aporta fibra, calcio y vitamina C.',
           'La flor de izote es la flor nacional de El Salvador y se cocina en tortas de huevo, con tomate o en escabeche.',
           'Siempre se consume cocida y se le retira el pistilo, que es la parte amarga de la flor.',
           'BORRADOR', NULL::timestamp, NULL::timestamp)
     ) AS e(nombre_cientifico, genero, especie_taxonomica, descripcion, origen, propiedades,
            importancia_cultural, advertencias, estado_publicacion, fecha_validacion, fecha_publicacion)
         JOIN taxonomia tx ON tx.genero = e.genero
                          AND tx.especie_taxonomica = e.especie_taxonomica
                          AND tx.subespecie IS NULL
WHERE NOT EXISTS (
          SELECT 1 FROM especie x WHERE lower(x.nombre_cientifico) = lower(e.nombre_cientifico)
      )
  AND NOT EXISTS (
          SELECT 1 FROM especie y WHERE y.id_taxonomia = tx.id_taxonomia
      );


-- =====================================================================
-- 13. Nombres comunes
-- =====================================================================
-- Se enlazan por nombre científico contra `especie`, así que también le
-- agregan nombres a las dos especies que ya estaban en la base.
--
-- Dos índices únicos vigilan esta tabla: uk_nombre_comun_especie_lower
-- (especie + nombre + región) y uk_nombre_comun_principal, que es parcial y
-- solo deja un nombre principal activo por especie. El ON CONFLICT sin
-- destino cubre los dos, que es lo que hace falta aquí: 'Inga paterno' ya
-- tiene un principal registrado y esa fila simplemente se descarta.

INSERT INTO nombre_comun (id_especie, nombre, region, es_principal, activo)
SELECT e.id_especie, n.nombre, n.region, n.es_principal, true
FROM (VALUES
          ('Fernaldia pandurata',      'Loroco',         'El Salvador', true),
          ('Fernaldia pandurata',      'Quilite',        'Guatemala',   false),

          ('Crotalaria longirostrata', 'Chipilín',       'El Salvador', true),
          ('Crotalaria longirostrata', 'Chepil',         'México',      false),
          ('Crotalaria longirostrata', 'Chipilín',       'Guatemala',   false),

          ('Solanum americanum',       'Hierba mora',    'El Salvador', true),
          ('Solanum americanum',       'Mora',           'El Salvador', false),
          ('Solanum americanum',       'Macuy',          'Guatemala',   false),

          ('Yucca guatemalensis',      'Flor de izote',  'El Salvador', true),
          ('Yucca guatemalensis',      'Izote',          'El Salvador', false),

          ('Chamaedorea tepejilote',   'Pacaya',         'El Salvador', true),
          ('Chamaedorea tepejilote',   'Tepejilote',     'Guatemala',   false),

          ('Byrsonima crassifolia',    'Nance',          'El Salvador', true),
          ('Byrsonima crassifolia',    'Nanche',         'México',      false),

          ('Spondias purpurea',        'Jocote',         'El Salvador', true),
          ('Spondias purpurea',        'Jocote de corona','El Salvador', false),
          ('Spondias purpurea',        'Ciruela mexicana','México',      false),

          ('Inga paterno',             'Paterna',        'El Salvador', true),
          ('Inga paterno',             'Cuajinicuil',    'Guatemala',   false)
     ) AS n(nombre_cientifico, nombre, region, es_principal)
         JOIN especie e ON lower(e.nombre_cientifico) = lower(n.nombre_cientifico)
ON CONFLICT DO NOTHING;


-- =====================================================================
-- 14. Verificación
-- =====================================================================
-- Mismo criterio que V7: si algo no quedó, la migración falla y Flyway la
-- revierte, en lugar de dejar la base a medias en silencio.
--
-- No se verifican conteos de filas insertadas (dependen de lo que ya hubiera
-- en la base), sino que el estado final sea el que el frontend necesita.

DO $$
DECLARE
    faltantes text;
    total     integer;
BEGIN
    -- Los dos usuarios de demostración, activos.
    SELECT string_agg(d.nombre_usuario, ', ')
    INTO faltantes
    FROM (VALUES ('admin.demo'), ('investigador.demo')) AS d(nombre_usuario)
    WHERE NOT EXISTS (
        SELECT 1 FROM usuario u
        WHERE u.nombre_usuario = d.nombre_usuario AND u.activo
    );

    IF faltantes IS NOT NULL THEN
        RAISE EXCEPTION 'Faltan usuarios de demostracion activos: %', faltantes;
    END IF;

    -- Los cuatro roles y su enlace con los usuarios de demostración.
    SELECT string_agg(d.nombre, ', ')
    INTO faltantes
    FROM (VALUES ('ADMINISTRADOR'), ('INVESTIGADOR'), ('EDITOR'), ('CONSULTOR')) AS d(nombre)
    WHERE NOT EXISTS (SELECT 1 FROM rol r WHERE r.nombre = d.nombre);

    IF faltantes IS NOT NULL THEN
        RAISE EXCEPTION 'Faltan roles: %', faltantes;
    END IF;

    SELECT count(*)
    INTO total
    FROM usuario_rol ur
             JOIN usuario u ON u.id_usuario = ur.id_usuario
             JOIN rol r ON r.id_rol = ur.id_rol
    WHERE (u.nombre_usuario = 'admin.demo' AND r.nombre = 'ADMINISTRADOR')
       OR (u.nombre_usuario = 'investigador.demo' AND r.nombre = 'INVESTIGADOR');

    IF total <> 2 THEN
        RAISE EXCEPTION 'Los usuarios de demostracion no quedaron enlazados a sus roles (% de 2)', total;
    END IF;

    -- Que admin.demo pueda hacer algo: su rol tiene que traer permisos.
    SELECT count(*)
    INTO total
    FROM rol_permiso rp
             JOIN rol r ON r.id_rol = rp.id_rol
    WHERE r.nombre = 'ADMINISTRADOR';

    IF total < 14 THEN
        RAISE EXCEPTION 'ADMINISTRADOR quedo con % permisos, se esperaban al menos 14', total;
    END IF;

    -- Las ocho especies, cada una con su taxonomía y al menos un nombre común.
    SELECT string_agg(d.nombre_cientifico, ', ')
    INTO faltantes
    FROM (VALUES ('Fernaldia pandurata'), ('Crotalaria longirostrata'),
                 ('Solanum americanum'), ('Yucca guatemalensis'),
                 ('Chamaedorea tepejilote'), ('Byrsonima crassifolia'),
                 ('Spondias purpurea'), ('Inga paterno')) AS d(nombre_cientifico)
    WHERE NOT EXISTS (
        SELECT 1
        FROM especie e
                 JOIN taxonomia t ON t.id_taxonomia = e.id_taxonomia
        WHERE lower(e.nombre_cientifico) = lower(d.nombre_cientifico)
          AND EXISTS (SELECT 1 FROM nombre_comun nc WHERE nc.id_especie = e.id_especie)
    );

    IF faltantes IS NOT NULL THEN
        RAISE EXCEPTION 'Faltan especies (o su taxonomia o sus nombres comunes): %', faltantes;
    END IF;

    -- Que el catálogo público tenga contenido.
    SELECT count(*)
    INTO total
    FROM especie
    WHERE estado_publicacion = 'PUBLICADA' AND activa;

    IF total < 5 THEN
        RAISE EXCEPTION 'Solo hay % especies publicadas y activas, se esperaban al menos 5', total;
    END IF;
END $$;
