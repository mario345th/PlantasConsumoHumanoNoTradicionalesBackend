package sv.edu.ues.fmp.flora.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sv.edu.ues.fmp.flora.entity.EspecieHabitat;
import sv.edu.ues.fmp.flora.entity.enums.EspecieHabitatId;

@Repository
public interface EspecieHabitatRepository extends JpaRepository<EspecieHabitat, EspecieHabitatId> {

    /**
     * Consulta las relaciones cuyo campo especie.idEspecie coincide con el ID.
     * Devuelve una lista vacía si no hay coincidencias; no valida que el padre exista.
     */
    List<EspecieHabitat> findByEspecieIdEspecie(Long idEspecie);

    /**
     * Consulta las relaciones cuyo campo habitat.idHabitat coincide con el ID.
     * Devuelve una lista vacía si no hay coincidencias; no valida que el padre exista.
     */
    List<EspecieHabitat> findByHabitatIdHabitat(Long idHabitat);
}
