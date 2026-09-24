package sv.edu.ues.fmp.flora.mapper;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.EspecieRequest;
import sv.edu.ues.fmp.flora.dto.response.EspecieResponse;
import sv.edu.ues.fmp.flora.dto.response.NombreComunResponse;
import sv.edu.ues.fmp.flora.entity.Especie;
import sv.edu.ues.fmp.flora.entity.NombreComun;
import sv.edu.ues.fmp.flora.entity.Taxonomia;
import sv.edu.ues.fmp.flora.entity.Usuario;

/**
 * Convierte entre {@link EspecieRequest}/{@link EspecieResponse} y la entidad
 * {@link Especie}.
 * <p>
 * El mapper no consulta repositorios: recibe del servicio las entidades ya
 * resueltas ({@link Taxonomia}, {@link Usuario}) y se limita a armar objetos.
 */
@Component
@RequiredArgsConstructor
public class EspecieMapper {

    /**
     * Orden de presentacion de los nombres comunes: el principal primero y el
     * resto alfabeticamente.
     * <p>
     * La primera clave es {@code !esPrincipal}, porque un Comparator natural
     * ordena false antes que true y lo que se quiere es justo lo contrario:
     * negando, el principal puntua false y encabeza la lista. Equivale a
     * ordenar por {@code esPrincipal} descendente.
     */
    private static final Comparator<NombreComunResponse> ORDEN_PRESENTACION =
            Comparator.comparing((NombreComunResponse n) -> !Boolean.TRUE.equals(n.getEsPrincipal()))
                    .thenComparing(NombreComunResponse::getNombre, String.CASE_INSENSITIVE_ORDER);

    private final TaxonomiaMapper taxonomiaMapper;
    private final NombreComunMapper nombreComunMapper;

    /**
     * Construye la especie a partir de entidades ya validadas por el servicio.
     * No asigna estado ni fechas: el estado lo pone el {@code @Builder.Default}
     * en BORRADOR y las fechas las escribe PostgreSQL.
     */
    public Especie toEntity(EspecieRequest request, Taxonomia taxonomia, Usuario creador) {
        return Especie.builder()
                .taxonomia(taxonomia)
                .nombreCientifico(request.getNombreCientifico())
                .descripcion(request.getDescripcion())
                .origen(request.getOrigen())
                .propiedades(request.getPropiedades())
                .importanciaCultural(request.getImportanciaCultural())
                .advertencias(request.getAdvertencias())
                .creadaPor(creador)
                .build();
    }

    /**
     * Actualiza solo los campos que el usuario puede editar.
     * Deliberadamente NO toca estado, fechas ni usuarios de validacion o
     * publicacion: eso solo cambia por las transiciones de estado del servicio.
     */
    public void updateEntity(Especie entity, EspecieRequest request) {
        entity.setNombreCientifico(request.getNombreCientifico());
        entity.setDescripcion(request.getDescripcion());
        entity.setOrigen(request.getOrigen());
        entity.setPropiedades(request.getPropiedades());
        entity.setImportanciaCultural(request.getImportanciaCultural());
        entity.setAdvertencias(request.getAdvertencias());
    }

    /**
     * Arma la ficha completa, nombres comunes incluidos.
     * <p>
     * Acceder a {@code entity.getNombresComunes()} dispara la consulta a
     * {@code nombre_comun} por ser una coleccion perezosa. Es correcto porque
     * {@code toResponse} siempre se invoca desde metodos {@code @Transactional}
     * del servicio, de modo que la sesion sigue abierta; llamarlo fuera de una
     * transaccion daria {@code LazyInitializationException}.
     */
    public EspecieResponse toResponse(Especie entity) {
        return EspecieResponse.builder()
                .idEspecie(entity.getIdEspecie())
                .nombreCientifico(entity.getNombreCientifico())
                .descripcion(entity.getDescripcion())
                .origen(entity.getOrigen())
                .propiedades(entity.getPropiedades())
                .importanciaCultural(entity.getImportanciaCultural())
                .advertencias(entity.getAdvertencias())
                .estadoPublicacion(entity.getEstadoPublicacion())
                .activa(entity.getActiva())
                .taxonomia(taxonomiaMapper.toResponse(entity.getTaxonomia()))
                .nombresComunes(nombresComunesVisibles(entity))
                .creadaPor(nombreCompleto(entity.getCreadaPor()))
                .validadaPor(nombreCompleto(entity.getValidadaPor()))
                .publicadaPor(nombreCompleto(entity.getPublicadaPor()))
                .fechaRegistro(entity.getFechaRegistro())
                .fechaActualizacion(entity.getFechaActualizacion())
                .fechaValidacion(entity.getFechaValidacion())
                .fechaPublicacion(entity.getFechaPublicacion())
                .build();
    }

    /**
     * Nombres comunes que se muestran en la ficha: solo los activos, ordenados
     * con {@link #ORDEN_PRESENTACION}.
     * <p>
     * La coleccion llega null en una especie recien construida por el builder,
     * que todavia no la tiene, y llega vacia cuando la especie no tiene nombres
     * o los tiene todos dados de baja. En los tres casos el Response sale con
     * lista vacia y nunca con null, para que el consumidor pueda recorrerla sin
     * comprobar nada.
     */
    private List<NombreComunResponse> nombresComunesVisibles(Especie entity) {
        List<NombreComun> nombres = entity.getNombresComunes();
        if (nombres == null || nombres.isEmpty()) {
            return List.of();
        }
        return nombres.stream()
                .filter(nombre -> Boolean.TRUE.equals(nombre.getActivo()))
                .map(nombreComunMapper::toResponse)
                .sorted(ORDEN_PRESENTACION)
                .toList();
    }

    /**
     * Aplica el mismo orden de presentacion a una lista que no viene de la
     * entidad. Lo necesita el servicio al crear una especie con sus nombres
     * anidados: en ese momento la coleccion perezosa de la especie recien
     * insertada sigue en null, asi que los nombres que se acaban de crear no
     * pueden salir de {@link #toResponse} y hay que colocarlos a mano. Vive
     * aqui, y no en el servicio, para que el criterio de orden no se duplique.
     */
    public List<NombreComunResponse> ordenarParaPresentacion(List<NombreComunResponse> nombres) {
        return nombres.stream().sorted(ORDEN_PRESENTACION).toList();
    }

    /**
     * {@code validadaPor} y {@code publicadaPor} son nulos mientras la ficha no
     * pase por esas etapas, asi que el nombre sale como null en vez de reventar
     * con NullPointerException.
     */
    private String nombreCompleto(Usuario usuario) {
        return usuario == null ? null : usuario.getNombres() + " " + usuario.getApellidos();
    }
}
