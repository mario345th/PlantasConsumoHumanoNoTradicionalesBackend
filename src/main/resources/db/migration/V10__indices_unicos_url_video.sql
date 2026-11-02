-- V10__indices_unicos_url_video.sql
-- Impide que una misma preparacion (o una misma especie) tenga dos videos
-- activos apuntando al mismo enlace.
-- Responde a los hallazgos VP-H03 y VE-H03 del 3er reporte de QA Testing:
-- la API aceptaba repetir la URL, y la preparacion 1 quedo con dos videos
-- apuntando al mismo video de YouTube.
--
-- El indice es PARCIAL (WHERE activo = true) a proposito: la baja logica
-- conserva el historial, asi que un video dado de baja no debe bloquear
-- volver a registrar esa misma URL. Es el mismo criterio del indice de imagen
-- principal (uk_imagen_principal_preparacion) y el mismo que comprueban los
-- servicios con existsBy...AndActivoTrue, que devuelven 409 antes de que la
-- base tenga que rechazar la sentencia.
--
-- El numero de version es V10 y no V9 porque V9__datos_prueba_frontend.sql ya
-- esta tomado en la rama feature/jwt-autenticacion. Al integrar ambas ramas
-- Flyway debe poder aplicar V9 antes que V10.

-- Las dos sentencias siguientes dan de baja los duplicados que ya existen en
-- la base antes de crear el indice, porque de lo contrario CREATE UNIQUE INDEX
-- falla y, con Flyway en el arranque, la aplicacion no levanta para nadie.
-- No se borra ninguna fila: solo se pone activo = false en las repetidas,
-- conservando la de id mas bajo de cada grupo (la primera registrada). Varias
-- de estas filas las creo QA al probar el duplicado.
UPDATE video_preparacion v
SET activo = false
WHERE v.activo = true
  AND EXISTS (SELECT 1
              FROM video_preparacion previo
              WHERE previo.activo = true
                AND previo.id_preparacion = v.id_preparacion
                AND previo.url_video = v.url_video
                AND previo.id_video_preparacion < v.id_video_preparacion);

UPDATE video_especie v
SET activo = false
WHERE v.activo = true
  AND EXISTS (SELECT 1
              FROM video_especie previo
              WHERE previo.activo = true
                AND previo.id_especie = v.id_especie
                AND previo.url_video = v.url_video
                AND previo.id_video < v.id_video);

CREATE UNIQUE INDEX uk_video_preparacion_url_activa
    ON video_preparacion (id_preparacion, url_video)
    WHERE activo = true;

CREATE UNIQUE INDEX uk_video_especie_url_activa
    ON video_especie (id_especie, url_video)
    WHERE activo = true;
