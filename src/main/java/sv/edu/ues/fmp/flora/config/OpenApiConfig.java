package sv.edu.ues.fmp.flora.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
// http://localhost:8080/swagger-ui.html

//Version 1.1 del proyecto
@Configuration
public class OpenApiConfig {

    /**
     * Nombre del esquema de seguridad. Se declara una sola vez en
     * {@code components} y se referencia por este mismo nombre en el
     * {@link SecurityRequirement} global, asi que las dos partes no se pueden
     * desincronizar.
     */
    private static final String ESQUEMA_BEARER = "bearerAuth";

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI().info(new Info()
                .title("Catálogo de plantas de consumo humano no tradicionales")
                .version("1.0.0")
                .description("Team Backend . Universidad de El Salvador, "
                        + "Facultad Multidisciplinaria Paracentral."))

                // Declara el esquema: con type HTTP + scheme "bearer", Swagger UI
                // pinta el boton "Authorize", pide el token pelado (sin escribir
                // "Bearer") y el mismo arma la cabecera
                // "Authorization: Bearer <token>" que espera el filtro JWT.
                // bearerFormat es solo documentacion para quien lea el contrato.
                .components(new Components()
                        .addSecuritySchemes(ESQUEMA_BEARER, new SecurityScheme()
                                .name(ESQUEMA_BEARER)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT que devuelve POST /api/auth/login en el campo "
                                        + "\"token\". Pegar solo el token: Swagger le agrega el "
                                        + "prefijo \"Bearer \".")))

                // Requisito global: el candado sale en TODOS los endpoints y el
                // token viaja en cada prueba sin tener que anotar controller por
                // controller.
                //
                // OJO: esto es solo la capa de documentacion. Quien decide que es
                // publico y que no es SecurityConfig, y ahi el catalogo y el login
                // siguen abiertos. Un candado en un endpoint publico de Swagger NO
                // significa que exija token: significa que si hay token, se manda.
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_BEARER));
    }
}