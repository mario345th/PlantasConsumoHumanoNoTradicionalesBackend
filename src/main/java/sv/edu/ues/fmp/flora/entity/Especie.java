package sv.edu.ues.fmp.flora.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import sv.edu.ues.fmp.flora.entity.enums.EstadoPublicacion;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ficha de una especie vegetal. Corresponde a la tabla {@code especie} y es el
 * nucleo del catalogo: varias tablas la referencian por llave foranea, por eso
 * la baja es logica ({@code activa = false}) y nunca un DELETE fisico.
 * <p>
 * La bandera de estado de esta tabla esta en <strong>femenino</strong>
 * ({@code activa}), a diferencia de {@code usuario.activo} o
 * {@code parte_planta.activo}.
 * <p>
 * Restricciones que viven solo en la base y no se expresan en JPA:
 * <ul>
 *   <li>{@code ck_especie_estado}: CHECK que limita {@code estado_publicacion}
 *       a los cuatro valores de {@link EstadoPublicacion}.</li>
 *   <li>{@code ck_especie_publicacion}: si el estado es PUBLICADA, entonces
 *       {@code validada_por}, {@code publicada_por} y {@code fecha_publicacion}
 *       tienen que estar asignados los tres.</li>
 *   <li>{@code ck_especie_validacion}: si hay {@code validada_por} tiene que
 *       haber tambien {@code fecha_validacion}.</li>
 *   <li>{@code uk_especie_nombre_cientifico_lower}: indice unico
 *       <em>funcional</em> sobre {@code lower(nombre_cientifico)}. JPA solo
 *       sabe declarar UNIQUE sobre la columna tal cual, asi que aqui no se
 *       declara; la deteccion de duplicados se hace en el servicio con
 *       consultas {@code ...IgnoreCase}.</li>
 *   <li>{@code idx_especie_estado}: indice sobre
 *       ({@code estado_publicacion}, {@code activa}) que sirve a la consulta
 *       del catalogo publico.</li>
 *   <li>{@code trg_especie_fecha_actualizacion}: trigger BEFORE UPDATE que
 *       reescribe {@code fecha_actualizacion} en cada modificacion.</li>
 * </ul>
 * Tanto {@code fecha_registro} (DEFAULT CURRENT_TIMESTAMP) como
 * {@code fecha_actualizacion} (DEFAULT mas trigger) las asigna PostgreSQL, por
 * eso van anotadas con {@link Generated}: Hibernate no las escribe y las relee
 * de la base tras cada INSERT o UPDATE. Sin esa anotacion la entidad en memoria
 * conservaria la fecha vieja que el trigger ya reescribio.
 * <p>
 * La unica coleccion inversa mapeada es {@code nombresComunes}, porque la ficha
 * de la especie no esta completa sin ellos y el Response los lleva siempre. Las
 * demas (partes comestibles, imagenes, habitats, fuentes) se recuperan por
 * repositorio cuando hagan falta.
 */
@Entity
@Table(name = "especie")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Especie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_especie", nullable = false, updatable = false)
    private Long idEspecie;

    /**
     * Relacion 1:1 obligatoria: {@code id_taxonomia} es NOT NULL y UNIQUE, de
     * modo que una taxonomia pertenece a una sola especie y no puede existir
     * sin ella. Por eso la taxonomia se crea junto con la especie.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_taxonomia", nullable = false, unique = true)
    private Taxonomia taxonomia;

    @Column(name = "nombre_cientifico", nullable = false, length = 250)
    private String nombreCientifico;

    @Column(name = "descripcion", nullable = false, columnDefinition = "text")
    private String descripcion;

    @Column(name = "origen", length = 250)
    private String origen;

    @Column(name = "propiedades", columnDefinition = "text")
    private String propiedades;

    @Column(name = "importancia_cultural", columnDefinition = "text")
    private String importanciaCultural;

    @Column(name = "advertencias", columnDefinition = "text")
    private String advertencias;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_publicacion", nullable = false, length = 12)
    private EstadoPublicacion estadoPublicacion = EstadoPublicacion.BORRADOR;

    @Builder.Default
    @Column(name = "activa", nullable = false)
    private Boolean activa = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creada_por", nullable = false)
    private Usuario creadaPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validada_por")
    private Usuario validadaPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publicada_por")
    private Usuario publicadaPor;

    @Generated(event = EventType.INSERT)
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private LocalDateTime fechaRegistro;

    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    /** La asigna el servicio al validar, junto con {@code validadaPor}. */
    @Column(name = "fecha_validacion")
    private LocalDateTime fechaValidacion;

    /** La asigna el servicio al publicar, junto con {@code publicadaPor}. */
    @Column(name = "fecha_publicacion")
    private LocalDateTime fechaPublicacion;

    /**
     * Nombres vernaculos de la especie. Es el lado inverso: el dueno de la
     * relacion es {@code NombreComun.especie}, que es quien tiene la columna
     * {@code id_especie}.
     * <p>
     * Sin {@code cascade} a proposito: los nombres se crean y se dan de baja
     * explicitamente por {@code NombreComunService}, que es donde viven sus
     * reglas (un solo principal activo, unicidad de nombre mas region). Dejar
     * que se propagaran desde aqui saltaria esas validaciones.
     * <p>
     * Sin {@code @Builder.Default}: la coleccion puede quedar en null, y de
     * hecho queda asi en una especie recien construida por el builder. Quien la
     * lea tiene que contemplar ese caso.
     */
    @OneToMany(mappedBy = "especie", fetch = FetchType.LAZY)
    private List<NombreComun> nombresComunes;
}
