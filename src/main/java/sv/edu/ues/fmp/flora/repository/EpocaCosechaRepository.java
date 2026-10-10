package sv.edu.ues.fmp.flora.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import sv.edu.ues.fmp.flora.entity.EpocaCosecha;

@Repository
public interface EpocaCosechaRepository extends JpaRepository<EpocaCosecha, Long> {

    List<EpocaCosecha>
    findByEspecieParteComestibleIdEspecieParteOrderByMesInicioAscMesFinAsc(
            Long idEspecieParte
    );

    List<EpocaCosecha>
    findByEspecieParteComestibleIdEspecieParteAndActivaTrueOrderByMesInicioAscMesFinAsc(
            Long idEspecieParte
    );

    Optional<EpocaCosecha>
    findByEspecieParteComestibleIdEspecieParteAndMesInicioAndMesFin(
            Long idEspecieParte,
            Short mesInicio,
            Short mesFin
    );
}