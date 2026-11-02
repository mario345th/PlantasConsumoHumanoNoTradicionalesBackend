package sv.edu.ues.fmp.flora.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import sv.edu.ues.fmp.flora.entity.VideoEspecie;

@Repository
public interface VideoEspecieRepository extends JpaRepository<VideoEspecie, Long> {

    List<VideoEspecie> findByEspecieIdEspecieOrderByIdVideoAsc(Long idEspecie);

    List<VideoEspecie> findByEspecieIdEspecieAndActivoTrueOrderByIdVideoAsc(Long idEspecie);

    /**
     * Comprueba si la especie ya tiene un video activo con esa URL, para
     * rechazar el duplicado con 409 antes de llegar al indice unico parcial
     * uk_video_especie_url_activa (hallazgo VE-H03).
     */
    boolean existsByEspecieIdEspecieAndUrlVideoAndActivoTrue(Long idEspecie, String urlVideo);

    /** Igual que el anterior, pero ignorando el propio registro al actualizarlo. */
    boolean existsByEspecieIdEspecieAndUrlVideoAndActivoTrueAndIdVideoNot(
            Long idEspecie, String urlVideo, Long idVideo);
}
