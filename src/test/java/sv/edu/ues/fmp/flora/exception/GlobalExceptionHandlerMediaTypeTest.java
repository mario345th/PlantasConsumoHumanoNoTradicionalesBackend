package sv.edu.ues.fmp.flora.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.executable.ExecutableValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import sv.edu.ues.fmp.flora.controller.VideoPreparacionController;
import sv.edu.ues.fmp.flora.dto.request.VideoPreparacionRequest;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Manejadores agregados a raiz del 3er reporte de QA.
 * <p>
 * El defecto reportado en Video Especie era un 500 al enviar el cuerpo con
 * {@code Content-Type: text/plain}, por no haber manejador para
 * {@link HttpMediaTypeNotSupportedException}. El de
 * {@link ConstraintViolationException} acompaña al {@code @Positive} del
 * nuevo POST: sin el, una violacion de parametro tambien saldria como 500.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerMediaTypeTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Mock
    private HttpServletRequest request;

    @Test
    void contentTypeNoSoportado_devuelve415ConElErrorResponseDelProyecto() {
        when(request.getRequestURI()).thenReturn("/api/especies/1/videos");
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(
                MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<ErrorResponse> respuesta = handler.manejarMediaTypeNoSoportado(ex, request);

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, respuesta.getStatusCode());
        ErrorResponse cuerpo = respuesta.getBody();
        assertNotNull(cuerpo, "La respuesta debe traer el ErrorResponse del proyecto");
        assertEquals(415, cuerpo.getEstado());
        assertNotNull(cuerpo.getTimestamp(), "El ErrorResponse lleva timestamp");
        assertFalse(cuerpo.getError().isBlank(), "El ErrorResponse lleva error");
        assertEquals("/api/especies/1/videos", cuerpo.getRuta());
        assertTrue(cuerpo.getMensaje().contains("text/plain"),
                "El mensaje debe nombrar el tipo recibido");
        assertTrue(cuerpo.getMensaje().contains(MediaType.APPLICATION_JSON_VALUE),
                "El mensaje debe nombrar los tipos admitidos");
    }

    @Test
    void contentTypeNoSoportadoSinTiposAdmitidos_nombraApplicationJson() {
        when(request.getRequestURI()).thenReturn("/api/videos-preparacion/preparacion/1");
        HttpMediaTypeNotSupportedException ex =
                new HttpMediaTypeNotSupportedException("sin tipo de contenido");

        ResponseEntity<ErrorResponse> respuesta = handler.manejarMediaTypeNoSoportado(ex, request);

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, respuesta.getStatusCode());
        assertNotNull(respuesta.getBody());
        assertTrue(respuesta.getBody().getMensaje().contains(MediaType.APPLICATION_JSON_VALUE),
                "Sin lista de tipos soportados se nombra application/json");
    }

    @Test
    void idDePathNoPositivo_devuelve400ConElNombreDelParametro() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/videos-preparacion/preparacion/-1");

        ConstraintViolationException ex =
                new ConstraintViolationException(violacionesDelPost(-1L));

        ResponseEntity<ErrorResponse> respuesta =
                handler.manejarViolacionesDeParametros(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, respuesta.getStatusCode());
        ErrorResponse cuerpo = respuesta.getBody();
        assertNotNull(cuerpo);
        assertEquals(400, cuerpo.getEstado());
        assertNotNull(cuerpo.getErroresValidacion(), "Debe detallar el campo que falló");
        // La ruta de la violacion es "crear.idPreparacion"; al cliente solo le
        // llega el ultimo tramo.
        assertTrue(cuerpo.getErroresValidacion().containsKey("idPreparacion"),
                "La clave debe ser el nombre del parámetro, no la ruta completa: "
                        + cuerpo.getErroresValidacion().keySet());
    }

    /**
     * Violaciones reales del {@code @Positive} del POST, obtenidas con el
     * validador de ejecutables; asi la ruta de propiedad es la misma que
     * produce Spring en tiempo de ejecucion.
     */
    private static Set<ConstraintViolation<?>> violacionesDelPost(Long idPreparacion) throws Exception {
        Method crear = VideoPreparacionController.class.getMethod(
                "crear", Long.class, VideoPreparacionRequest.class);
        VideoPreparacionRequest valido = VideoPreparacionRequest.builder()
                .titulo("Elaboración paso a paso de la receta")
                .urlVideo("https://www.youtube.com/watch?v=chipilin01")
                .build();

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            ExecutableValidator validadorDeEjecutables = factory.getValidator().forExecutables();
            return Set.copyOf(validadorDeEjecutables.validateParameters(
                    new VideoPreparacionController(null),
                    crear,
                    new Object[] { idPreparacion, valido }));
        }
    }
}
