package sv.edu.ues.fmp.flora.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Datos que entran al crear o actualizar una especie.
 * <p>
 * No incluye {@code idEspecie}, {@code estadoPublicacion}, {@code activa},
 * {@code creadaPor}, {@code validadaPor}, {@code publicadaPor} ni fecha
 * alguna: todos esos los controla el servidor a traves de las transiciones de
 * estado del servicio.
 * <p>
 * {@code creadaPor} estaba aqui y se quito: el creador se toma del usuario
 * autenticado ({@code UsuarioAutenticado}), nunca del cuerpo. Si un cliente lo
 * sigue enviando se ignora en silencio, igual que los demas campos que no
 * estan en este DTO.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EspecieRequest {

    @NotBlank(message = "El nombre científico es obligatorio")
    @Size(max = 250, message = "El nombre científico no puede exceder 250 caracteres")
    private String nombreCientifico;

    /** La columna es NOT NULL en la base, por eso {@code @NotBlank} y no opcional. */
    @NotBlank(message = "La descripción es obligatoria")
    private String descripcion;

    @Size(max = 250, message = "El origen no puede exceder 250 caracteres")
    private String origen;

    private String propiedades;

    private String importanciaCultural;

    private String advertencias;

    /**
     * Los datos taxonomicos viajan anidados porque la relacion es 1:1
     * obligatoria en ambos sentidos.
     * El {@code @Valid} es imprescindible: sin el, las validaciones de
     * {@link TaxonomiaRequest} no se ejecutan.
     */
    @NotNull(message = "La taxonomía es obligatoria")
    @Valid
    private TaxonomiaRequest taxonomia;

    /**
     * Nombres vernaculos con los que se dara de alta la especie. Es opcional:
     * si llega null o vacio la especie se crea sin nombres comunes, que es
     * valido, porque se pueden agregar despues por los endpoints propios del
     * modulo. Si llega con elementos, exactamente uno tiene que venir marcado
     * como principal; eso lo comprueba el servicio.
     * <p>
     * El {@code @Valid} sobre la lista propaga las validaciones de cada
     * {@link NombreComunRequest}; sin el no se ejecutarian.
     * <p>
     * El {@code @NotNull} va sobre el ELEMENTO de la lista, no sobre la lista,
     * y no es lo mismo que el {@code @Valid}. {@code @Valid} cascada las
     * reglas de cada {@code NombreComunRequest} --su {@code @NotBlank nombre}
     * incluido-- pero un elemento que llega nulo
     * ({@code "nombresComunes": [null]}) no tiene ninguna regla que
     * incumplir, asi que pasaba la validacion entera y reventaba despues en el
     * servicio, al leerle {@code esPrincipal} a un null: un 500 por un cuerpo
     * mal formado. Con la restriccion sobre el elemento, ese cuerpo se queda
     * en el 400 del {@code MethodArgumentNotValidException}, con el mensaje de
     * abajo y sin que nadie haya leido nada del elemento.
     * <p>
     * Solo se tiene en cuenta al crear. En un PUT se ignora: la lista de
     * nombres comunes de una especie ya existente se gestiona por su modulo.
     */
    @Valid
    private List<
            @NotNull(message = "Cada nombre común de la lista debe venir con sus datos; "
                    + "no se admiten elementos nulos")
            NombreComunRequest> nombresComunes;
}
