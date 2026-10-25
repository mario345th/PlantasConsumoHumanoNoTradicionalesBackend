package sv.edu.ues.fmp.flora.config;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Politica CORS del proyecto.
 * <p>
 * Antes de esta clase el proyecto no tenia ninguna: ni {@code @CrossOrigin},
 * ni {@code WebMvcConfigurer}, ni {@code CorsConfigurationSource}. Se agrega
 * aqui porque el {@link SecurityConfig} necesita un
 * {@link CorsConfigurationSource} que conectar, y porque el frontend va a
 * empezar a mandar la cabecera {@code Authorization}, que en una peticion
 * entre origenes distintos dispara el preflight {@code OPTIONS}.
 * <p>
 * Es puramente aditiva: concede permiso a los origenes de la lista y no quita
 * nada de lo que hoy funciona. Una peticion del mismo origen no pasa por CORS,
 * y una de un origen que no este en la lista falla igual que fallaba ayer.
 * <p>
 * Los origenes salen de {@code app.cors.allowed-origins} (lista separada por
 * comas). No esta en {@code application.properties} a proposito: el valor por
 * omision cubre el desarrollo local y, cuando el equipo apruebe los origenes
 * reales, basta con declarar la propiedad o la variable de entorno
 * {@code APP_CORS_ALLOWED_ORIGINS} en el despliegue, sin tocar codigo.
 * <p>
 * {@code allowCredentials} queda en false: la sesion viaja en el token del
 * encabezado {@code Authorization}, no en una cookie, asi que no hay nada que
 * acreditar. Mantenerlo en false es lo que permite listar origenes concretos
 * sin caer en el comodin, que el navegador rechaza junto con credenciales.
 */
@Configuration
public class CorsConfig {

    private final List<String> origenesPermitidos;

    public CorsConfig(
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
            List<String> origenesPermitidos) {

        this.origenesPermitidos = origenesPermitidos;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(origenesPermitidos);
        configuracion.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("*"));
        // Allow la usa el 405 del GlobalExceptionHandler; sin exponerla el
        // navegador se la oculta al JavaScript que hizo la llamada.
        configuracion.setExposedHeaders(List.of(HttpHeaders.ALLOW));
        configuracion.setAllowCredentials(false);
        configuracion.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource origen = new UrlBasedCorsConfigurationSource();
        origen.registerCorsConfiguration("/**", configuracion);
        return origen;
    }
}
