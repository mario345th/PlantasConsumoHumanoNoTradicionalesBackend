package sv.edu.ues.fmp.flora.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * Por defecto Jackson es permisivo: si un DTO espera String y llega un numero
 * o un booleano, los convierte silenciosamente a texto en vez de rechazar la
 * peticion. Esto permitia que "genero": 1789967020 se guardara como si fuera
 * un valor de texto legitimo (ver hallazgo QA sobre coercion implicita en
 * Especies y en Partes de Planta).
 * <p>
 * Esta configuracion desactiva esa conversion automatica para numeros y
 * booleanos: si el tipo no coincide, Jackson lanza InvalidFormatException,
 * que el GlobalExceptionHandler traduce en un 400 con mensaje especifico.
 * <p>
 * Spring Boot 4 lee los cuerpos JSON con Jackson 3 (paquete
 * {@code tools.jackson}); Jackson 2 ({@code com.fasterxml.jackson.databind})
 * solo llega al classpath como dependencia de springdoc y no interviene en
 * las peticiones. Por eso se usa {@link JsonMapperBuilderCustomizer} y no
 * {@code Jackson2ObjectMapperBuilderCustomizer}, que en Boot 4 no existe sin
 * el modulo {@code spring-boot-jackson2}. La coercion se configura sobre el
 * builder porque en Jackson 3 el mapper ya construido es inmutable.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer coercionEstrictaCustomizer() {
        return builder -> builder.withCoercionConfig(LogicalType.Textual, config -> config
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail));
    }
}
