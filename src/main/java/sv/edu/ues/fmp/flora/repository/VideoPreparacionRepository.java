package sv.edu.ues.fmp.flora.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import sv.edu.ues.fmp.flora.entity.VideoPreparacion;

/** Consultas de videos asociados a preparaciones de consumo. */
@Repository
public interface VideoPreparacionRepository extends JpaRepository<VideoPreparacion, Long> {


    List<VideoPreparacion> findByPreparacionIdPreparacionOrderByIdVideoPreparacionAsc(Long idPreparacion);


    List<VideoPreparacion> findByPreparacionIdPreparacionAndActivoTrueOrderByIdVideoPreparacionAsc(Long idPreparacion);


    /**
     * Comprueba si la preparacion ya tiene un video activo con esa URL, para
     * rechazar el duplicado con 409 antes de llegar al indice unico parcial
     * uk_video_preparacion_url_activa (hallazgo VP-H03).
     * <p>
     * Solo mira los activos, igual que el indice: un video dado de baja no
     * bloquea volver a registrar la misma URL.
     */
    boolean existsByPreparacionIdPreparacionAndUrlVideoAndActivoTrue(Long idPreparacion, String urlVideo);


    /** Igual que el anterior, pero ignorando el propio registro al actualizarlo. */
    boolean existsByPreparacionIdPreparacionAndUrlVideoAndActivoTrueAndIdVideoPreparacionNot(
            Long idPreparacion, String urlVideo, Long idVideoPreparacion);
}

