package sv.edu.ues.fmp.flora.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sv.edu.ues.fmp.flora.dto.request.VideoEspecieRequest;
import sv.edu.ues.fmp.flora.dto.request.VideoPreparacionRequest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validaciones de los DTO de video exigidas por el 3er reporte de QA:
 * VP-H02 (URL laxa), VP-H04 (XSS en campos de texto) y VP-H06 (duracion sin
 * tope). Las mismas reglas valen para los dos modulos, asi que cada caso se
 * comprueba en VideoPreparacionRequest y en VideoEspecieRequest.
 */
class VideoRequestValidationTest {

    private static final String URL_VALIDA = "https://www.youtube.com/watch?v=chipilin01";

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    // ---------- VP-H04 / XSS ----------

    @Test
    void videoPreparacion_tituloConEtiquetaScript_esRechazado() {
        VideoPreparacionRequest request = preparacionValida();
        request.setTitulo("<script>alert('xss')</script>");

        assertTrue(fallaEn(validator.validate(request), "titulo"),
                "El título con < y > debe rechazarse (VP-H04)");
    }

    @Test
    void videoEspecie_tituloConEtiquetaScript_esRechazado() {
        VideoEspecieRequest request = especieValida();
        request.setTitulo("<script>alert('xss')</script>");

        assertTrue(fallaEn(validator.validate(request), "titulo"),
                "El título con < y > debe rechazarse (VP-H04)");
    }

    @Test
    void videoPreparacion_descripcionYCreditosConAngulos_sonRechazados() {
        VideoPreparacionRequest conDescripcion = preparacionValida();
        conDescripcion.setDescripcion("Receta <b>destacada</b>");
        assertTrue(fallaEn(validator.validate(conDescripcion), "descripcion"),
                "La descripción con < y > debe rechazarse");

        VideoPreparacionRequest conCreditos = preparacionValida();
        conCreditos.setCreditos("Canal <tradicional>");
        assertTrue(fallaEn(validator.validate(conCreditos), "creditos"),
                "Los créditos con < y > deben rechazarse");
    }

    @Test
    void videoEspecie_descripcionYCreditosConAngulos_sonRechazados() {
        VideoEspecieRequest conDescripcion = especieValida();
        conDescripcion.setDescripcion("Especie <b>destacada</b>");
        assertTrue(fallaEn(validator.validate(conDescripcion), "descripcion"),
                "La descripción con < y > debe rechazarse");

        VideoEspecieRequest conCreditos = especieValida();
        conCreditos.setCreditos("Canal <tradicional>");
        assertTrue(fallaEn(validator.validate(conCreditos), "creditos"),
                "Los créditos con < y > deben rechazarse");
    }

    // ---------- VP-H02 / URL estricta ----------

    @Test
    void videoPreparacion_urlSinDominio_esRechazada() {
        VideoPreparacionRequest request = preparacionValida();
        request.setUrlVideo("https://a");

        assertTrue(fallaEn(validator.validate(request), "urlVideo"),
                "\"https://a\" ya no debe aceptarse: falta dominio con punto y TLD (VP-H02)");
    }

    @Test
    void videoEspecie_urlSinDominio_esRechazada() {
        VideoEspecieRequest request = especieValida();
        request.setUrlVideo("https://a");

        assertTrue(fallaEn(validator.validate(request), "urlVideo"),
                "\"https://a\" ya no debe aceptarse: falta dominio con punto y TLD (VE/VP-H02)");
    }

    @Test
    void videoPreparacion_urlConDominioYPuerto_esAceptada() {
        VideoPreparacionRequest request = preparacionValida();
        request.setUrlVideo("http://videos.ues.edu.sv:8080/recursos/chipilin.mp4");

        assertTrue(validator.validate(request).isEmpty(),
                "Una URL con dominio, TLD, puerto y ruta debe aceptarse");
    }

    // ---------- VP-H06 / tope de duracion ----------

    @Test
    void videoPreparacion_duracionDesmedida_esRechazada() {
        VideoPreparacionRequest request = preparacionValida();
        request.setDuracionSegundos(999_999_999);

        assertTrue(fallaEn(validator.validate(request), "duracionSegundos"),
                "999999999 segundos debe rechazarse por el tope de 14400 (VP-H06)");
    }

    @Test
    void videoEspecie_duracionDesmedida_esRechazada() {
        VideoEspecieRequest request = especieValida();
        request.setDuracionSegundos(999_999_999);

        assertTrue(fallaEn(validator.validate(request), "duracionSegundos"),
                "999999999 segundos debe rechazarse por el tope de 14400 (VP-H06)");
    }

    @Test
    void videoPreparacion_duracionEnElTope_esAceptada() {
        VideoPreparacionRequest request = preparacionValida();
        request.setDuracionSegundos(14_400);

        assertFalse(fallaEn(validator.validate(request), "duracionSegundos"),
                "El límite de 14400 segundos es inclusivo");
    }

    // ---------- caso base ----------

    @Test
    void requestsValidos_noGeneranViolaciones() {
        assertTrue(validator.validate(preparacionValida()).isEmpty(),
                "El request de preparación de referencia debe ser válido");
        assertTrue(validator.validate(especieValida()).isEmpty(),
                "El request de especie de referencia debe ser válido");
    }

    private static VideoPreparacionRequest preparacionValida() {
        return VideoPreparacionRequest.builder()
                .titulo("Elaboración paso a paso de la receta")
                .descripcion("Explicación del tiempo de cocción")
                .urlVideo(URL_VALIDA)
                .duracionSegundos(180)
                .creditos("Canal de Cocina Tradicional")
                .build();
    }

    private static VideoEspecieRequest especieValida() {
        return VideoEspecieRequest.builder()
                .titulo("Cultivo tradicional de Chipilín")
                .descripcion("Recorrido por la parcela")
                .urlVideo(URL_VALIDA)
                .duracionSegundos(180)
                .creditos("Canal de Agricultura")
                .build();
    }

    private static boolean fallaEn(Set<? extends ConstraintViolation<?>> violaciones, String campo) {
        return violaciones.stream()
                .anyMatch(v -> campo.equals(v.getPropertyPath().toString()));
    }
}
