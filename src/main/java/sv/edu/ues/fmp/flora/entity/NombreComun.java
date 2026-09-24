package sv.edu.ues.fmp.flora.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Nombre vernaculo con el que se conoce una especie en una region.
 * Corresponde a la tabla {@code nombre_comun} y depende de {@link Especie}:
 * no existe un nombre comun sin la especie a la que nombra.
 * <p>
 * La bandera de estado de esta tabla esta en <strong>masculino</strong>
 * ({@code activo}), como {@code parte_planta.activo} y a diferencia de
 * {@code especie.activa}. La baja es logica, nunca un DELETE fisico.
 * <p>
 * Restricciones que viven solo en la base y no se expresan en JPA:
 * <ul>
 *   <li>{@code uk_nombre_comun_principal}: indice unico <em>parcial</em>
 *       sobre ({@code id_especie}) WHERE {@code es_principal = true AND
 *       activo = true}. Es la regla "un solo nombre principal activo por
 *       especie" impuesta por la base. JPA no sabe declarar indices
 *       parciales, asi que aqui no se declara; el servicio la anticipa
 *       desmarcando el principal anterior dentro de la misma transaccion.</li>
 *   <li>{@code uk_nombre_comun_especie_lower}: indice unico <em>funcional</em>
 *       sobre ({@code id_especie}, {@code lower(nombre)},
 *       {@code coalesce(lower(region), '')}). JPA solo sabe declarar UNIQUE
 *       sobre las columnas tal cual, asi que aqui no se declara; la deteccion
 *       de duplicados vive en el servicio, apoyada en las consultas JPQL del
 *       repositorio que replican ese {@code lower(...)} y ese
 *       {@code coalesce(...)}. Notese que la region forma parte de la clave:
 *       el mismo nombre puede repetirse en la especie si cambia la region.</li>
 *   <li>{@code idx_nombre_comun_busqueda}: indice sobre {@code lower(nombre)}
 *       que sirve a la busqueda de especies por nombre vernaculo.</li>
 * </ul>
 * No se mapea la coleccion inversa desde {@link Especie}: los nombres se
 * recuperan por repositorio cuando hacen falta.
 */
@Entity
@Table(name = "nombre_comun")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NombreComun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_nombre_comun", nullable = false, updatable = false)
    private Long idNombreComun;

    /**
     * Especie a la que pertenece el nombre. Es obligatoria y no cambia una vez
     * creado el registro: para mover un nombre de especie se crea otro.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_especie", nullable = false)
    private Especie especie;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    /** Zona donde se usa el nombre. Es opcional: un nombre puede ser nacional. */
    @Column(name = "region", length = 150)
    private String region;

    @Builder.Default
    @Column(name = "es_principal", nullable = false)
    private Boolean esPrincipal = false;

    @Builder.Default
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;
}
