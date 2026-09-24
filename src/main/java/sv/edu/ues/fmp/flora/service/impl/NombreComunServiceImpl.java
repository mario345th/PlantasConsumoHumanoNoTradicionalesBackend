package sv.edu.ues.fmp.flora.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.NombreComunRequest;
import sv.edu.ues.fmp.flora.dto.response.NombreComunResponse;
import sv.edu.ues.fmp.flora.entity.Especie;
import sv.edu.ues.fmp.flora.entity.NombreComun;
import sv.edu.ues.fmp.flora.exception.EstadoInvalidoException;
import sv.edu.ues.fmp.flora.exception.RecursoDuplicadoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.mapper.NombreComunMapper;
import sv.edu.ues.fmp.flora.repository.EspecieRepository;
import sv.edu.ues.fmp.flora.repository.NombreComunRepository;
import sv.edu.ues.fmp.flora.service.NombreComunService;

/**
 * Implementacion de la logica de negocio de los nombres comunes.
 * <p>
 * Reglas que viven aqui:
 * <ul>
 *   <li><strong>R1</strong>: una especie tiene como mucho un nombre principal
 *       y activo. La base la respalda con el indice unico parcial
 *       {@code uk_nombre_comun_principal}.</li>
 *   <li><strong>R2</strong>: al marcar un nombre como principal, el que lo
 *       fuera antes se desmarca en la misma transaccion. Ver
 *       {@link #desmarcarPrincipalActual(Long)}.</li>
 *   <li><strong>R3</strong>: no se desactiva el unico principal si la especie
 *       conserva otros nombres activos, porque quedaria con alternativas pero
 *       sin nombre de cabecera. Si es el unico activo si se permite.</li>
 *   <li><strong>R4</strong>: dentro de una misma especie no se repite la
 *       combinacion nombre + region, aunque el mismo nombre si puede usarse en
 *       dos regiones distintas de esa especie, y desde luego en otras especies.
 *       Es exactamente lo que exige el indice
 *       {@code uk_nombre_comun_especie_lower}.</li>
 * </ul>
 * Un CRUD dependiente necesita ademas el repositorio del padre, para poder
 * distinguir "la especie no existe" (404) de "la especie no tiene nombres"
 * (200 con lista vacia).
 */
@Service
@RequiredArgsConstructor
public class NombreComunServiceImpl implements NombreComunService {

    private final NombreComunRepository nombreComunRepository;
    private final EspecieRepository especieRepository;
    private final NombreComunMapper nombreComunMapper;

    @Override
    @Transactional(readOnly = true)
    public List<NombreComunResponse> listarPorEspecie(Long idEspecie) {
        verificarEspecieOFallar(idEspecie);
        return nombreComunRepository.findByEspecieIdEspecie(idEspecie)
                .stream().map(nombreComunMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NombreComunResponse> listarActivosPorEspecie(Long idEspecie) {
        verificarEspecieOFallar(idEspecie);
        return nombreComunRepository.findByEspecieIdEspecieAndActivoTrue(idEspecie)
                .stream().map(nombreComunMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public NombreComunResponse obtenerPorId(Long id) {
        return nombreComunMapper.toResponse(buscarOFallar(id));
    }

    @Override
    @Transactional
    public NombreComunResponse crear(Long idEspecie, NombreComunRequest request) {
        // El padre tiene que existir
        Especie especie = buscarEspecieOFallar(idEspecie);

        // La region se compara ya normalizada, con la misma regla que usara el
        // mapper al construir la entidad: si se validara el valor crudo del
        // request y se insertara el normalizado, la comprobacion y el INSERT
        // mirarian valores distintos y el duplicado se colaria.
        String region = nombreComunMapper.normalizarRegion(request.getRegion());

        // Duplicado DENTRO de esa especie y para esa misma region, no global (R4)
        if (nombreComunRepository.existeNombreEnEspecie(idEspecie, request.getNombre(), region)) {
            throw new RecursoDuplicadoException(
                    "Ya existe el nombre '" + request.getNombre() + "' "
                            + describirRegion(region)
                            + " en la especie " + especie.getNombreCientifico() + ".");
        }

        // R2: el nuevo principal desplaza al anterior antes de entrar
        if (Boolean.TRUE.equals(request.getEsPrincipal())) {
            desmarcarPrincipalActual(idEspecie);
        }

        NombreComun nuevo = nombreComunMapper.toEntity(request, especie);
        return nombreComunMapper.toResponse(nombreComunRepository.save(nuevo));
    }

    @Override
    @Transactional
    public NombreComunResponse actualizar(Long id, NombreComunRequest request) {
        NombreComun entidad = buscarOFallar(id);
        Long idEspecie = entidad.getEspecie().getIdEspecie();

        // Normalizada por el mismo motivo que en crear(): la comprobacion tiene
        // que mirar el valor que realmente se va a guardar.
        String region = nombreComunMapper.normalizarRegion(request.getRegion());

        // R4: el choque solo importa si el nombre o la region cambiaron. Al
        // conservarlos, la unica coincidencia posible es el propio registro, y el
        // filter por id la descarta para que se pueda guardar sin cambiar nada.
        nombreComunRepository.buscarPorNombreYRegion(idEspecie, request.getNombre(), region)
                .filter(otro -> !otro.getIdNombreComun().equals(id))
                .ifPresent(otro -> {
                    throw new RecursoDuplicadoException(
                            "Ya existe otro nombre '" + request.getNombre() + "' "
                                    + describirRegion(region)
                                    + " en esta especie.");
                });

        // R2, pero solo en la transicion false -> true: si la entidad ya era la
        // principal, el unico registro que devolveria la consulta seria ella
        // misma, y desmarcarla dejaria a la especie sin principal.
        boolean promocion = Boolean.TRUE.equals(request.getEsPrincipal())
                && !Boolean.TRUE.equals(entidad.getEsPrincipal());
        if (promocion) {
            desmarcarPrincipalActual(idEspecie);
        }

        nombreComunMapper.updateEntity(entidad, request);
        return nombreComunMapper.toResponse(entidad);
    }

    @Override
    @Transactional
    public NombreComunResponse marcarComoPrincipal(Long id) {
        NombreComun entidad = buscarOFallar(id);

        if (!Boolean.TRUE.equals(entidad.getActivo())) {
            throw new EstadoInvalidoException(
                    "Un nombre común desactivado no puede marcarse como principal.");
        }

        // Idempotente: repetir la promocion del que ya es principal no es un
        // error, simplemente no hay nada que cambiar.
        if (Boolean.TRUE.equals(entidad.getEsPrincipal())) {
            return nombreComunMapper.toResponse(entidad);
        }

        desmarcarPrincipalActual(entidad.getEspecie().getIdEspecie());
        entidad.setEsPrincipal(true);

        return nombreComunMapper.toResponse(entidad);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        NombreComun entidad = buscarOFallar(id);

        if (!Boolean.TRUE.equals(entidad.getActivo())) {
            return;
        }

        // R3: dar de baja al principal deja a la especie sin nombre de cabecera.
        // Solo se tolera cuando tampoco le quedan alternativas, es decir cuando
        // este es el ultimo nombre activo que le queda.
        if (Boolean.TRUE.equals(entidad.getEsPrincipal())) {
            long activos = nombreComunRepository.countByEspecieIdEspecieAndActivoTrue(
                    entidad.getEspecie().getIdEspecie());
            if (activos > 1) {
                throw new EstadoInvalidoException(
                        "No se puede desactivar el nombre principal mientras existan otros "
                                + "nombres activos. Marque otro como principal primero.");
            }
        }

        entidad.setActivo(false);
    }

    @Override
    @Transactional
    public NombreComunResponse activar(Long id) {
        NombreComun entidad = buscarOFallar(id);

        if (Boolean.TRUE.equals(entidad.getActivo())) {
            return nombreComunMapper.toResponse(entidad);
        }

        // R2 al revivir: mientras estuvo de baja, otro nombre pudo tomar el
        // puesto de principal. Se le cede a la entidad que vuelve, que es la que
        // el usuario esta reactivando explicitamente.
        if (Boolean.TRUE.equals(entidad.getEsPrincipal())) {
            desmarcarPrincipalActual(entidad.getEspecie().getIdEspecie());
        }

        entidad.setActivo(true);

        return nombreComunMapper.toResponse(entidad);
    }

    /**
     * Regla R2: deja a la especie sin ningun nombre marcado como principal y
     * activo, para que el llamador pueda poner el suyo.
     * <p>
     * Todos los llamadores comprueban antes que la entidad que van a promover no
     * sea ya el principal activo, asi que aqui nunca se desmarca a si misma.
     */
    private void desmarcarPrincipalActual(Long idEspecie) {
        nombreComunRepository.findByEspecieIdEspecieAndEsPrincipalTrueAndActivoTrue(idEspecie)
                .ifPresent(anterior -> {
                    anterior.setEsPrincipal(false);

                    // No se llama a save(): 'anterior' es una entidad gestionada
                    // dentro de esta transaccion, asi que el UPDATE lo emite
                    // Hibernate por su cuenta, via dirty checking.
                    //
                    // El flush() no contradice lo anterior: no guarda nada extra,
                    // unicamente adelanta ese UPDATE. Hace falta porque Hibernate
                    // ordena su cola de acciones poniendo TODOS los INSERT antes
                    // que los UPDATE, y porque el orden entre dos UPDATE de la
                    // misma tabla tampoco esta garantizado. Sin el flush, la fila
                    // que se promueve podria escribirse mientras la anterior sigue
                    // marcada, y el indice unico parcial uk_nombre_comun_principal
                    // (id_especie WHERE es_principal AND activo) rechazaria la
                    // operacion: es un indice, no una constraint DEFERRABLE, de
                    // modo que PostgreSQL lo comprueba sentencia a sentencia y no
                    // al cierre de la transaccion.
                    nombreComunRepository.flush();
                });
    }

    /**
     * Arma el fragmento de mensaje que situa el conflicto de la regla R4.
     * La region es opcional, y decir "para la región 'null'" seria peor que no
     * decir nada, asi que ese caso tiene su propia redaccion.
     */
    private String describirRegion(String region) {
        return (region == null || region.isBlank())
                ? "sin región asignada"
                : "para la región '" + region + "'";
    }

    private NombreComun buscarOFallar(Long id) {
        return nombreComunRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el nombre común con id " + id));
    }

    private Especie buscarEspecieOFallar(Long idEspecie) {
        return especieRepository.findById(idEspecie)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la especie con id " + idEspecie));
    }

    /** Version barata para los listados, que no necesitan la especie cargada. */
    private void verificarEspecieOFallar(Long idEspecie) {
        if (!especieRepository.existsById(idEspecie)) {
            throw new RecursoNoEncontradoException("No existe la especie con id " + idEspecie);
        }
    }
}
