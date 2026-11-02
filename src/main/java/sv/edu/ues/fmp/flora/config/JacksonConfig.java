package sv.edu.ues.fmp.flora.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;


@Configuration
public class JacksonConfig {

    /**
     * Desactiva las conversiones automaticas de Jackson en los cuerpos de
     * peticion: un campo tiene que llegar con el tipo JSON que le corresponde,
     * no con uno que Jackson sepa convertir por su cuenta.
     * <p>
     *
     * Hacia {@link LogicalType#Textual} se rechaza el numero, el booleano y el
     * decimal donde va texto: {@code "nombre": 42} no es un nombre.
     * <p>
     * Hacia {@link LogicalType#Integer} se rechazan dos formas que QA observo
     * en los ids del cuerpo:
     * <ul>
     *   <li>{@code String} -> {@code "idUsuario": "4"}, que Jackson aceptaba
     *       convirtiendo la cadena a 4.</li>
     *   <li>{@code Float} -> {@code "idUsuario": 7.9}, que Jackson aceptaba
     *       truncando a 7. Truncar en silencio es lo peor de los dos casos:
     *       la peticion se guardaba contra un id que el cliente nunca pidio.</li>
     * </ul>
     * Ambas devuelven ahora 400 por
     * {@code HttpMessageNotReadableException}, con el mismo mensaje por campo
     * que ya emitia el GlobalExceptionHandler para los demas errores de tipo.
     * <p>
     * Alcance: afecta a <strong>toda</strong> la API, no solo a un modulo,
     * porque el customizer se aplica al JsonMapper que comparten todos los
     * controladores. Las {@code @PathVariable} y los {@code @RequestParam} no
     * cambian: esos los convierte Spring con su propio
     * {@code ConversionService}, sin pasar por Jackson, asi que
     * {@code /api/especies/qa} sigue dando el 400 de
     * {@code MethodArgumentTypeMismatchException} de siempre.
     * <p>
     * Hacia {@link LogicalType#Float} se rechaza el texto y el booleano donde
     * va un decimal. Es el bloque que anticipaba la nota de esta misma clase:
     * los {@code BigDecimal} de {@code UbicacionEspecieRequest} hicieron que
     * dejara de ser hipotetico. QA observo (AN-H09) que {@code "idEspecie":
     * "3"} se rechazaba pero {@code "latitud": "13.66"} se aceptaba y se
     * guardaba, con lo que dos campos numericos del mismo cuerpo se trataban
     * distinto.
     * <p>
     * No se rechaza {@link CoercionInputShape#Integer} hacia Float: enviar
     * {@code "latitud": 14} en lugar de {@code 14.0} es un entero que
     * representa exactamente el mismo decimal, sin perdida ni ambiguedad.
     */
    @Bean
    public JsonMapperBuilderCustomizer coercionEstrictaCustomizer() {
        return builder -> {
            builder.withCoercionConfig(LogicalType.Textual, config -> config
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail));

            builder.withCoercionConfig(LogicalType.Integer, config -> config
                    .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail));

            builder.withCoercionConfig(LogicalType.Float, config -> config
                    .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
        };
    }
}
