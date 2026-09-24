package sv.edu.ues.fmp.flora.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Captura en un solo lugar las excepciones de toda la API y las convierte en
 * respuestas HTTP con un cuerpo {@link ErrorResponse} uniforme.
 * Gracias a esto los controladores quedan libres de bloques try/catch.
 * <p>
 * El manejador de {@link DataIntegrityViolationException} es una <em>red de
 * seguridad</em>, no el mecanismo previsto: cubre de golpe las restricciones de
 * las 28 tablas del esquema, pero su mensaje es necesariamente generico. Si un
 * cliente recibe ese 409 generico, significa que a algun servicio le falta
 * anticipar su propia restriccion y devolver un mensaje especifico.
 */
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

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cuerpo);
    }

    /**
     * El registro chocaria con uno existente -> 409 CONFLICT.
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

        return ResponseEntity.status(HttpStatus.CONFLICT).body(cuerpo);
    }

    /**
     * Ultima red: una restriccion de la base rechazo la operacion y ningun
     * servicio la anticipo -> 409 CONFLICT.
     * <p>
     * El mensaje es deliberadamente generico. El detalle de la excepcion
     * incluye el nombre de la restriccion y el SQL que fallo, y el sistema
     * tiene un area publica: esa informacion no debe salir en una respuesta
     * HTTP. Para diagnosticar queda el log del servidor.
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

        return ResponseEntity.status(HttpStatus.CONFLICT).body(cuerpo);
    }

    /**
     * La transicion de estado pedida no esta permitida por el flujo editorial
     * -> 409 CONFLICT. Es un conflicto con el estado actual del recurso, no un
     * error de formato de la peticion, por eso 409 y no 400.
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

        return ResponseEntity.status(HttpStatus.CONFLICT).body(cuerpo);
    }

    /**
     * Login fallido (usuario/correo inexistente, clave incorrecta o cuenta
     * desactivada) -> 401 UNAUTHORIZED. El mensaje es siempre el mismo para
     * no revelar cual de esas tres cosas paso.
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

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(cuerpo);
    }

    /**
     * El id recibido (path variable) no es un valor valido -> 400 BAD REQUEST.
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

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cuerpo);
    }

    /**
     * Un path variable o request param no se pudo convertir al tipo esperado
     * (por ejemplo, "/api/usuarios/abc") -> 400 BAD REQUEST.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> manejarTipoInvalido(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(HttpStatus.BAD_REQUEST.value())
                .error("Parámetro inválido")
                .mensaje("El valor de '" + ex.getName() + "' no tiene el formato esperado")
                .ruta(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cuerpo);
    }

    /**
     * Falla alguna anotacion de Bean Validation en un {@code @Valid @RequestBody}
     * -> 400 BAD REQUEST con el detalle campo por campo.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> manejarErroresDeValidacion(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        // Se recorren todos los errores de campo para que el cliente reciba de
        // una sola vez todo lo que debe corregir, y no solo el primer fallo.
        Map<String, String> erroresValidacion = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            erroresValidacion.put(error.getField(), error.getDefaultMessage());
        }

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(HttpStatus.BAD_REQUEST.value())
                .error("Error de validación")
                .mensaje("La solicitud contiene campos inválidos")
                .ruta(request.getRequestURI())
                .erroresValidacion(erroresValidacion)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cuerpo);
    }

    /**
     * El cuerpo de la peticion no se pudo leer: JSON con sintaxis invalida, o un
     * tipo incompatible en un campo (numero donde se esperaba texto, booleano
     * donde se esperaba texto) una vez activada la coercion estricta de Jackson
     * en JacksonConfig -> 400 BAD REQUEST sin exponer el stack trace interno.
     * <p>
     * La causa real viene envuelta dentro de HttpMessageNotReadableException:
     * si es InvalidFormatException o MismatchedInputException, se identifica el
     * campo culpable; en cualquier otro caso (JSON con sintaxis rota, comas
     * faltantes, llaves sin cerrar) se da un mensaje generico de formato.
     * <p>
     * Ambas excepciones son las de Jackson 3 ({@code tools.jackson}), que es el
     * que usa Spring Boot 4 para leer el cuerpo. Las homonimas de Jackson 2
     * ({@code com.fasterxml.jackson.databind.exc}) tambien compilan, porque
     * springdoc las trae al classpath, pero nunca coincidirian con la causa.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> manejarJsonInvalido(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        String mensaje = "El cuerpo de la solicitud no tiene un formato JSON valido";

        String campo = null;
        Throwable causa = ex.getCause();
        if (causa instanceof InvalidFormatException ife) {
            campo = nombreDelCampo(ife.getPath());
        } else if (causa instanceof MismatchedInputException mie) {
            campo = nombreDelCampo(mie.getPath());
        }
        if (campo != null) {
            mensaje = "El campo '" + campo + "' tiene un tipo de dato incorrecto";
        }

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(HttpStatus.BAD_REQUEST.value())
                .error("Solicitud mal formada")
                .mensaje(mensaje)
                .ruta(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cuerpo);
    }

    /**
     * Devuelve el nombre del ultimo tramo de la ruta que corresponde a un campo.
     * Los tramos que son indices de lista no tienen nombre: en
     * {@code "nombresComunes": [123]} la ruta es nombresComunes -> [0], y el
     * campo que hay que reportar es nombresComunes, no el indice.
     * Devuelve null si ningun tramo tiene nombre (por ejemplo, si el cuerpo
     * entero es una lista), y entonces se conserva el mensaje generico.
     */
    private static String nombreDelCampo(List<JacksonException.Reference> ruta) {
        for (int i = ruta.size() - 1; i >= 0; i--) {
            String nombre = ruta.get(i).getPropertyName();
            if (nombre != null) {
                return nombre;
            }
        }
        return null;
    }
}
