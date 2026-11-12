package sv.edu.ues.fmp.flora.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

/**
 * Pruebas de las dos validaciones del secreto HS256.
 *
 * <h2>Por que no levanta la aplicacion completa</h2>
 * Lo natural seria una prueba con {@code @SpringBootTest} y
 * {@code @ActiveProfiles("prod")} comprobando que el contexto no arranca. No
 * se hace asi porque ese contexto arrastra el datasource y Flyway: la prueba
 * fallaria igual sin base de datos y no se sabria si lo que la rompio fue el
 * secreto o Postgres. En su lugar hay dos niveles:
 * <ul>
 *   <li>{@link JwtConfig#validarSecreto(String, java.util.Collection)} probado
 *       directamente, sin Spring;</li>
 *   <li>{@link ApplicationContextRunner} levantando <em>solo</em>
 *       {@link JwtConfig}, que es lo que demuestra que la validacion esta de
 *       verdad conectada al arranque del bean y no solo disponible en un
 *       metodo que nadie llama.</li>
 * </ul>
 */
class JwtConfigSecretoTest {

    /** Una clave cualquiera que cumple los 32 bytes. */
    private static final String SECRETO_PROPIO =
            "un-secreto-de-despliegue-suficientemente-largo-2026";

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(JwtConfig.class);

    // ---------- La validacion, sin Spring ----------

    @Test
    void secretoDeDesarrollo_conPerfilProd_noSeAdmite() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> JwtConfig.validarSecreto(JwtConfig.SECRETO_DE_DESARROLLO, List.of("prod")));

        assertTrue(error.getMessage().startsWith("Define la variable JWT_SECRET en producción"),
                "el mensaje tiene que decir que hay que definir JWT_SECRET: " + error.getMessage());
    }

    /**
     * Los tres nombres de perfil que el codigo reconoce como produccion, mas
     * uno en mayusculas y otro acompañado de un perfil cualquiera: en un
     * despliegue real {@code prod} casi nunca va solo.
     */
    @Test
    void secretoDeDesarrollo_noSeAdmiteConNingunaFormaDeNombrarProduccion() {
        List<List<String>> perfiles = List.of(
                List.of("prod"),
                List.of("produccion"),
                List.of("production"),
                List.of("PROD"),
                List.of("metrics", "prod", "correo"));

        for (List<String> activos : perfiles) {
            assertThrows(IllegalStateException.class,
                    () -> JwtConfig.validarSecreto(JwtConfig.SECRETO_DE_DESARROLLO, activos),
                    "deberia rechazarse con los perfiles " + activos);
        }
    }

    @Test
    void secretoDeDesarrollo_sinPerfiles_siSeAdmite() {
        JwtConfig.validarSecreto(JwtConfig.SECRETO_DE_DESARROLLO, List.of());
        JwtConfig.validarSecreto(JwtConfig.SECRETO_DE_DESARROLLO, List.of("dev"));
        JwtConfig.validarSecreto(JwtConfig.SECRETO_DE_DESARROLLO, List.of("test", "local"));
        JwtConfig.validarSecreto(JwtConfig.SECRETO_DE_DESARROLLO, List.of("DEV", " desarrollo "));
    }

    /**
     * SEG-PB-02: la comprobacion es una lista blanca, no una lista de nombres
     * de produccion.
     * <p>
     * Antes solo se reconocian {@code prod}, {@code produccion} y
     * {@code production}, asi que un despliegue con cualquier otro nombre de
     * perfil firmaba con la clave que esta en el repositorio y nada avisaba.
     * Estos son los nombres que un despliegue real usa mas a menudo; ninguno es
     * de desarrollo, asi que ninguno admite el marcador.
     */
    @Test
    void secretoDeDesarrollo_conUnPerfilQueNoEsDeDesarrollo_noSeAdmite() {
        List<List<String>> perfiles = List.of(
                List.of("qa"),
                List.of("staging"),
                List.of("uat"),
                List.of("docker"),
                List.of("demo"),
                // Un perfil de desarrollo acompañado de uno que no lo es
                // tampoco alcanza: si algo que no es local esta activo, el
                // ambiente ya no es la maquina de quien programa.
                List.of("dev", "qa"));

        for (List<String> activos : perfiles) {
            assertThrows(IllegalStateException.class,
                    () -> JwtConfig.validarSecreto(JwtConfig.SECRETO_DE_DESARROLLO, activos),
                    "deberia rechazarse con los perfiles " + activos);
        }
    }

    /** Un secreto propio vale en cualquier ambiente, de desarrollo o no. */
    @Test
    void secretoPropio_siSeAdmiteConCualquierPerfil() {
        JwtConfig.validarSecreto(SECRETO_PROPIO, List.of("qa"));
        JwtConfig.validarSecreto(SECRETO_PROPIO, List.of("staging", "metrics"));
        JwtConfig.validarSecreto(SECRETO_PROPIO, List.of());
    }

    @Test
    void secretoPropio_siSeAdmiteEnProduccion() {
        JwtConfig.validarSecreto(SECRETO_PROPIO, List.of("prod"));
    }

    @Test
    void secretoCorto_noSeAdmiteNiEnDesarrollo() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> JwtConfig.validarSecreto("demasiado-corto", List.of()));

        assertTrue(error.getMessage().contains("al menos 32 bytes"),
                "el mensaje tiene que decir cuantos bytes faltan: " + error.getMessage());
    }

    /**
     * El limite se mide en bytes UTF-8, no en caracteres: 32 letras con tilde
     * pasan de 32 bytes, pero 20 no llegan aunque parezcan suficientes.
     */
    @Test
    void elLimiteSeMideEnBytesNoEnCaracteres() {
        String veinteAcentos = "á".repeat(20);
        assertEquals(40, veinteAcentos.getBytes(StandardCharsets.UTF_8).length);
        JwtConfig.validarSecreto(veinteAcentos, List.of());

        String quinceAcentos = "á".repeat(15);
        assertEquals(30, quinceAcentos.getBytes(StandardCharsets.UTF_8).length);
        assertThrows(IllegalStateException.class,
                () -> JwtConfig.validarSecreto(quinceAcentos, List.of()));
    }

    // ---------- La validacion conectada al arranque del bean ----------

    @Test
    void elContextoNoArranca_conPerfilProdYSecretoDeDesarrollo() {
        contexto.withPropertyValues(
                        "app.jwt.secret=" + JwtConfig.SECRETO_DE_DESARROLLO,
                        "spring.profiles.active=prod")
                .run(resultado -> {
                    assertTrue(resultado.getStartupFailure() != null,
                            "el contexto no deberia haber arrancado");
                    assertTrue(causaRaizDe(resultado.getStartupFailure())
                                    .getMessage()
                                    .startsWith("Define la variable JWT_SECRET en producción"),
                            "el arranque tiene que fallar por el secreto");
                });
    }

    @Test
    void elContextoArranca_conPerfilProdYSecretoPropio() {
        contexto.withPropertyValues(
                        "app.jwt.secret=" + SECRETO_PROPIO,
                        "spring.profiles.active=prod")
                .run(resultado -> {
                    assertTrue(resultado.getStartupFailure() == null,
                            "el contexto deberia haber arrancado");
                    assertNotNull(resultado.getBean(JwtEncoder.class));
                    assertNotNull(resultado.getBean(JwtDecoder.class));
                });
    }

    @Test
    void elContextoArranca_enDesarrolloConElSecretoDeDesarrollo() {
        contexto.withPropertyValues("app.jwt.secret=" + JwtConfig.SECRETO_DE_DESARROLLO)
                .run(resultado -> assertTrue(resultado.getStartupFailure() == null,
                        "levantar en local no deberia pedir configurar nada"));
    }

    private static Throwable causaRaizDe(Throwable error) {
        Throwable actual = error;
        while (actual.getCause() != null) {
            actual = actual.getCause();
        }
        return actual;
    }

    // ---------- Que la constante no se separe del properties ----------

    /**
     * La constante {@link JwtConfig#SECRETO_DE_DESARROLLO} duplica el valor por
     * omision que esta en {@code application.properties}. Si alguien cambia uno
     * y no el otro, la validacion de produccion deja de reconocer el marcador y
     * el agujero vuelve sin que nada falle. Esta prueba es la que avisa.
     */
    @Test
    void laConstanteCoincideConElValorPorOmisionDelProperties() throws IOException {
        Properties propiedades = new Properties();
        try (InputStream entrada = getClass().getResourceAsStream("/application.properties")) {
            assertNotNull(entrada, "no se encontro application.properties en el classpath");
            propiedades.load(entrada);
        }

        String declarado = propiedades.getProperty("app.jwt.secret");
        assertNotNull(declarado, "app.jwt.secret no esta declarado en application.properties");

        // La propiedad se escribe como ${JWT_SECRET:<valor por omision>}; aqui
        // se lee el literal sin resolver, que es justo lo que hace falta.
        String prefijo = "${JWT_SECRET:";
        assertTrue(declarado.startsWith(prefijo) && declarado.endsWith("}"),
                "app.jwt.secret deberia salir de JWT_SECRET con un valor por omision: " + declarado);

        String porOmision = declarado.substring(prefijo.length(), declarado.length() - 1);

        assertEquals(porOmision, JwtConfig.SECRETO_DE_DESARROLLO,
                "la constante SECRETO_DE_DESARROLLO se separo del valor de application.properties; "
                        + "si el marcador cambio a proposito, actualiza la constante");
    }
}
