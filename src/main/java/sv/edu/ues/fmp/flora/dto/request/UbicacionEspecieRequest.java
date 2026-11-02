package sv.edu.ues.fmp.flora.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.OptBoolean;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UbicacionEspecieRequest {

    /**
     * Caja envolvente de El Salvador, redondeada hacia fuera.
     * <p>
     * QA reporto (AN-H01) que el POST y el PUT aceptaban cualquier punto entre
     * ±90 y ±180: Guatemala, Honduras, el mar, el Polo Norte y puntos con el
     * signo de la longitud invertido. El catalogo es solo de plantas de El
     * Salvador, asi que el rango se cierra al territorio nacional.
     * <p>
     * Es una caja, no la frontera real: deja pasar algunos puntos de los
     * paises vecinos y del Pacifico que caen dentro del rectangulo. Afinar eso
     * pide el polígono del pais, que la base no tiene (ver AN-H02 en
     * {@code UbicacionEspecieServiceImpl}).
     */
    private static final String LATITUD_MINIMA = "13.1";
    private static final String LATITUD_MAXIMA = "14.5";
    private static final String LONGITUD_MINIMA = "-90.2";
    private static final String LONGITUD_MAXIMA = "-87.6";

    private static final String MENSAJE_FUERA_DEL_PAIS =
            "Las coordenadas deben estar dentro de El Salvador";

    /** Igual criterio que en los modulos de video: nada de < ni > en texto libre. */
    private static final String PATRON_SIN_ETIQUETAS = "^[^<>]*$";

    /** Primer anio aceptable para una observacion de campo (AN-H11). */
    private static final int ANIO_MINIMO_OBSERVACION = 1900;

    @NotNull(message = "El ID de la especie es obligatorio")
    @Positive(message = "El ID de la especie debe ser un número válido mayor a cero")
    private Long idEspecie;

    @NotNull(message = "El ID del distrito es obligatorio")
    @Positive(message = "El ID del distrito debe ser un número válido mayor a cero")
    private Long idDistrito;

    @NotBlank(message = "El nombre del lugar no puede estar vacío")
    @Size(max = 200, message = "El nombre del lugar no debe exceder 200 caracteres")
    @Pattern(regexp = PATRON_SIN_ETIQUETAS,
            message = "El nombre del lugar no puede contener los caracteres < ni >")
    private String nombreLugar;

    /**
     * {@code @Digits} con 7 decimales acompania a la columna
     * {@code numeric(9,7)}: antes la respuesta devolvia los 11 decimales
     * enviados mientras la base guardaba 7, y el front mostraba un valor
     * distinto al real (AN-H07). Ahora el exceso de decimales se rechaza con
     * 400 en lugar de redondearse en silencio.
     */
    @NotNull(message = "La latitud es obligatoria")
    @DecimalMin(value = LATITUD_MINIMA, message = MENSAJE_FUERA_DEL_PAIS)
    @DecimalMax(value = LATITUD_MAXIMA, message = MENSAJE_FUERA_DEL_PAIS)
    @Digits(integer = 2, fraction = 7,
            message = "La latitud admite 2 dígitos enteros y hasta 7 decimales")
    private BigDecimal latitud;

    /** La columna de longitud es {@code numeric(10,7)}: 3 enteros y 7 decimales. */
    @NotNull(message = "La longitud es obligatoria")
    @DecimalMin(value = LONGITUD_MINIMA, message = MENSAJE_FUERA_DEL_PAIS)
    @DecimalMax(value = LONGITUD_MAXIMA, message = MENSAJE_FUERA_DEL_PAIS)
    @Digits(integer = 3, fraction = 7,
            message = "La longitud admite 3 dígitos enteros y hasta 7 decimales")
    private BigDecimal longitud;

    @Pattern(regexp = PATRON_SIN_ETIQUETAS,
            message = "La descripción no puede contener los caracteres < ni >")
    private String descripcion;

    /**
     * Solo fecha, en {@code yyyy-MM-dd} y sin lectura leniente.
     * <p>
     * Antes "2026-10-15T10:30:00" se aceptaba descartando la hora en silencio
     * y "15/10/2026" devolvia un "tipo de dato incorrecto" que no decia el
     * formato esperado (AN-H10). Las dos formas dan ahora 400 indicando el
     * formato; el mensaje lo arma el GlobalExceptionHandler.
     * <p>
     * El patron usa {@code uuuu} y no {@code yyyy} a proposito. Con
     * {@code lenient = FALSE} Jackson resuelve la fecha en modo estricto, y en
     * ese modo {@code yyyy} es el "anio de la era", que sin un campo de era
     * acompanante no se puede resolver: el patron {@code yyyy-MM-dd} rechazaba
     * incluso una fecha correcta como 2026-10-15. {@code uuuu} es el anio
     * proleptico y si resuelve. Al usuario se le sigue hablando de
     * {@code yyyy-MM-dd}, que es la notacion que conoce.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "uuuu-MM-dd", lenient = OptBoolean.FALSE)
    @PastOrPresent(message = "La fecha de observación no puede ser una fecha futura")
    @Schema(description = "Fecha de la observación en formato yyyy-MM-dd", example = "2026-10-15")
    private LocalDate fechaObservacion;

    /**
     * Descarta observaciones con fecha inverosimil: QA registro una aceptada
     * con fecha de 1800, porque solo se comprobaba que no fuera futura
     * (AN-H11). Se deja como {@code @AssertTrue} porque Bean Validation no
     * trae un minimo para fechas, siguiendo el mismo patron que
     * {@code ImagenEspecieRequest.isUrlValida()}.
     */
    @Schema(hidden = true)
    @JsonIgnore
    @AssertTrue(message = "La fecha de observación no puede ser anterior al año 1900")
    public boolean isFechaObservacionRazonable() {
        return fechaObservacion == null || fechaObservacion.getYear() >= ANIO_MINIMO_OBSERVACION;
    }
}
