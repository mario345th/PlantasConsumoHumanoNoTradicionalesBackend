package sv.edu.ues.fmp.flora.exception;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.InvalidNullException;
import tools.jackson.databind.exc.MismatchedInputException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * El recurso pedido no existe -> 404 NOT FOUND.
     */
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> manejarRecursoNoEncontrado(
            RecursoNoEncontradoException ex,
            HttpServletRequest request) {

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(HttpStatus.NOT_FOUND.value())
                .error("Recurso no encontrado")
                .mensaje(ex.getMessage())
                .ruta(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(cuerpo);
    }

    /**
     * El registro chocaría con uno existente -> 409 CONFLICT.
     */
    @ExceptionHandler(RecursoDuplicadoException.class)
    public ResponseEntity<ErrorResponse> manejarRecursoDuplicado(
            RecursoDuplicadoException ex,
            HttpServletRequest request) {

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(HttpStatus.CONFLICT.value())
                .error("Conflicto")
                .mensaje(ex.getMessage())
                .ruta(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(cuerpo);
    }

    /**
     * Manejo específico para duplicados de Beneficio.
     */
    @ExceptionHandler(DatoDuplicadoException.class)
    public ResponseEntity<ErrorResponse> manejarDatoDuplicado(
            DatoDuplicadoException ex,
            HttpServletRequest request) {

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(HttpStatus.CONFLICT.value())
                .error("Conflicto")
                .mensaje(ex.getMessage())
                .ruta(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(cuerpo);
    }

    /**
     * Última red: una restricción de la base rechazó la operación.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> manejarIntegridad(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(HttpStatus.CONFLICT.value())
                .error("Conflicto de integridad")
                .mensaje("La operación viola una restricción de la base de datos")
                .ruta(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(cuerpo);
    }

    /**
     * Transición de estado inválida -> 409 CONFLICT.
     */
    @ExceptionHandler(EstadoInvalidoException.class)
    public ResponseEntity<ErrorResponse> manejarEstadoInvalido(
            EstadoInvalidoException ex,
            HttpServletRequest request) {

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(HttpStatus.CONFLICT.value())
                .error("Transición de estado no válida")
                .mensaje(ex.getMessage())
                .ruta(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(cuerpo);
    }

    /**
     * Login fallido -> 401 UNAUTHORIZED.
     */
    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ErrorResponse> manejarCredencialesInvalidas(
            CredencialesInvalidasException ex,
            HttpServletRequest request) {

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(HttpStatus.UNAUTHORIZED.value())
                .error("No autorizado")
                .mensaje(ex.getMessage())
                .ruta(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(cuerpo);
    }

    /**
     * ID inválido -> 400 BAD REQUEST.
     */
    @ExceptionHandler(IdInvalidoException.class)
    public ResponseEntity<ErrorResponse> manejarIdInvalido(
            IdInvalidoException ex,
            HttpServletRequest request) {

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(HttpStatus.BAD_REQUEST.value())
                .error("Id inválido")
                .mensaje(ex.getMessage())
                .ruta(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(cuerpo);
    }

    /**
     * PathVariable o RequestParam con tipo incorrecto.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> manejarTipoInvalido(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(HttpStatus.BAD_REQUEST.value())
                .error("Parámetro inválido")
                .mensaje("El valor de '" + ex.getName()
                        + "' no tiene el formato esperado")
                .ruta(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(cuerpo);
    }

    /**
     * Errores de Bean Validation.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> manejarErroresDeValidacion(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> erroresValidacion = new HashMap<>();

        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            erroresValidacion.put(
                    error.getField(),
                    error.getDefaultMessage()
            );
        }

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(HttpStatus.BAD_REQUEST.value())
                .error("Error de validación")
                .mensaje("La solicitud contiene campos inválidos")
                .ruta(request.getRequestURI())
                .erroresValidacion(erroresValidacion)
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(cuerpo);
    }

    /**
     * JSON inválido, enum incorrecto, null no permitido o tipo incompatible.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> manejarJsonInvalido(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        String mensaje =
                "El cuerpo de la solicitud no tiene un formato JSON válido";

        Throwable causa = ex.getCause();

        /*
         * Caso 1:
         * Enum inválido.
         *
         * Ejemplo:
         * "tipoBeneficio": "PRUEBA"
         */
        if (causa instanceof InvalidFormatException error) {

            String campo = nombreDelCampo(error.getPath());
            Class<?> tipo = error.getTargetType();

            if (tipo != null && tipo.isEnum()) {

                String valoresPermitidos =
                        Arrays.stream(tipo.getEnumConstants())
                                .map(Object::toString)
                                .collect(Collectors.joining(", "));
                mensaje =
                        "El valor '" + error.getValue()
                                + "' no es válido para el campo '"
                                + campo
                                + "'. Valores permitidos: "
                                + valoresPermitidos;
            } else if (campo != null) {
                mensaje =
                        "El campo '" + campo
                                + "' tiene un tipo de dato incorrecto";
            }
            /*
             * Caso 2:
             * Campo enviado explícitamente como null.
             *
             * Ejemplo:
             * "activo": null
             */
        } else if (causa instanceof InvalidNullException error) {

            String campo = nombreDelCampo(error.getPath());

            if (campo != null) {
                mensaje =
                        "El campo '" + campo
                                + "' no puede ser nulo";
            }

            /*
             * Caso 3:
             * Otro tipo incompatible.
             */
        } else if (causa instanceof MismatchedInputException error) {

            String campo = nombreDelCampo(error.getPath());

            if (campo != null) {
                mensaje =
                        "El campo '" + campo
                                + "' tiene un tipo de dato incorrecto";
            }
        }

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(HttpStatus.BAD_REQUEST.value())
                .error("Solicitud mal formada")
                .mensaje(mensaje)
                .ruta(request.getRequestURI())
                .build();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(cuerpo);
    }

    /**
     * Devuelve el nombre del último campo presente en la ruta de Jackson.
     */
    private static String nombreDelCampo(
            List<JacksonException.Reference> ruta) {
        for (int i = ruta.size() - 1; i >= 0; i--) {
            String nombre = ruta.get(i).getPropertyName();
            if (nombre != null) {
                return nombre;
            }
        }
        return null;
    }
}