package sv.edu.ues.fmp.flora.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sv.edu.ues.fmp.flora.dto.request.UbicacionEspecieRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validaciones de UbicacionEspecieRequest exigidas por el 3er reporte de QA:
 * AN-H01 (coordenadas fuera de El Salvador), AN-H04 (XSS), AN-H07 (decimales
 * de mas) y AN-H11 (fecha no razonable).
 */
class UbicacionEspecieRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    // ---------- AN-H01: coordenadas fuera del pais ----------

    @Test
    void coordenadasDeGuatemala_sonRechazadas() {
        UbicacionEspecieRequest request = valida();
        request.setLatitud(new BigDecimal("14.6349"));
        request.setLongitud(new BigDecimal("-90.5069"));

        Set<ConstraintViolation<UbicacionEspecieRequest>> violaciones = validator.validate(request);

        assertTrue(falla(violaciones, "latitud") || falla(violaciones, "longitud"),
                "Ciudad de Guatemala queda fuera de El Salvador (AN-H01)");
        assertTrue(violaciones.stream()
                        .anyMatch(v -> v.getMessage().contains("dentro de El Salvador")),
                "El mensaje debe decir que las coordenadas deben estar dentro de El Salvador");
    }

    @Test
    void longitudConSignoInvertido_esRechazada() {
        UbicacionEspecieRequest request = valida();
        request.setLongitud(new BigDecimal("88.9"));

        assertTrue(falla(validator.validate(request), "longitud"),
                "La longitud positiva cae en el hemisferio equivocado (AN-H01)");
    }

    @Test
    void poloNorte_esRechazado() {
        UbicacionEspecieRequest request = valida();
        request.setLatitud(new BigDecimal("90.0"));
        request.setLongitud(new BigDecimal("0.0"));

        Set<ConstraintViolation<UbicacionEspecieRequest>> violaciones = validator.validate(request);

        assertTrue(falla(violaciones, "latitud"), "90 de latitud ya no se acepta");
        assertTrue(falla(violaciones, "longitud"), "0 de longitud ya no se acepta");
    }

    @Test
    void coordenadasDeSanVicente_sonAceptadas() {
        UbicacionEspecieRequest request = valida();
        request.setLatitud(new BigDecimal("13.6417"));
        request.setLongitud(new BigDecimal("-88.7850"));

        assertTrue(validator.validate(request).isEmpty(),
                "Un punto real de El Salvador debe seguir siendo válido");
    }

    // ---------- AN-H04: XSS ----------

    @Test
    void nombreLugarConEtiquetaScript_esRechazado() {
        UbicacionEspecieRequest request = valida();
        request.setNombreLugar("<script>alert('xss')</script>");

        assertTrue(falla(validator.validate(request), "nombreLugar"),
                "El nombre del lugar con < y > debe rechazarse (AN-H04)");
    }

    @Test
    void descripcionConAngulos_esRechazada() {
        UbicacionEspecieRequest request = valida();
        request.setDescripcion("Ladera <b>norte</b>");

        assertTrue(falla(validator.validate(request), "descripcion"),
                "La descripción con < y > debe rechazarse (AN-H04)");
    }

    // ---------- AN-H07: decimales de mas ----------

    @Test
    void coordenadasConOnceDecimales_sonRechazadas() {
        UbicacionEspecieRequest request = valida();
        request.setLatitud(new BigDecimal("13.70123456789"));
        request.setLongitud(new BigDecimal("-88.98765432109"));

        Set<ConstraintViolation<UbicacionEspecieRequest>> violaciones = validator.validate(request);

        assertTrue(falla(violaciones, "latitud"), "La columna guarda 7 decimales, no 11 (AN-H07)");
        assertTrue(falla(violaciones, "longitud"), "La columna guarda 7 decimales, no 11 (AN-H07)");
    }

    @Test
    void coordenadasConSieteDecimales_sonAceptadas() {
        UbicacionEspecieRequest request = valida();
        request.setLatitud(new BigDecimal("13.7012345"));
        request.setLongitud(new BigDecimal("-88.9876543"));

        assertTrue(validator.validate(request).isEmpty(), "7 decimales es justo lo que cabe");
    }

    // ---------- AN-H11: fecha no razonable ----------

    @Test
    void fechaDeObservacionDe1800_esRechazada() {
        UbicacionEspecieRequest request = valida();
        request.setFechaObservacion(LocalDate.of(1800, 1, 1));

        assertTrue(validator.validate(request).stream()
                        .anyMatch(v -> v.getMessage().contains("1900")),
                "Una observación de 1800 no es razonable (AN-H11)");
    }

    @Test
    void fechaDeObservacionReciente_esAceptada() {
        UbicacionEspecieRequest request = valida();
        request.setFechaObservacion(LocalDate.of(2026, 10, 15));

        assertTrue(validator.validate(request).isEmpty(), "Una fecha reciente debe aceptarse");
    }

    @Test
    void fechaDeObservacionNula_esAceptada() {
        UbicacionEspecieRequest request = valida();
        request.setFechaObservacion(null);

        assertFalse(falla(validator.validate(request), "fechaObservacionRazonable"),
                "La fecha es opcional: sin valor no debe fallar el mínimo");
    }

    private static UbicacionEspecieRequest valida() {
        return UbicacionEspecieRequest.builder()
                .idEspecie(1L)
                .idDistrito(1L)
                .nombreLugar("Cerro Las Pavas")
                .latitud(new BigDecimal("13.6417"))
                .longitud(new BigDecimal("-88.7850"))
                .descripcion("Ladera norte, cafetal de sombra")
                .fechaObservacion(LocalDate.of(2026, 10, 15))
                .build();
    }

    private static boolean falla(Set<ConstraintViolation<UbicacionEspecieRequest>> violaciones,
                                 String campo) {
        return violaciones.stream().anyMatch(v -> campo.equals(v.getPropertyPath().toString()));
    }
}
