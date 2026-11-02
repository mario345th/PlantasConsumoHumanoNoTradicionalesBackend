package sv.edu.ues.fmp.flora.exception;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.exc.InputCoercionException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.InvalidNullException;
import tools.jackson.databind.exc.MismatchedInputException;


@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
    /**
     * Demasiados intentos de login fallidos -> 429 TOO MANY REQUESTS.
     * <p>
     * Es el codigo que corresponde: la peticion esta bien formada y las
     * credenciales podrian incluso ser correctas, lo que sobra es la
     * frecuencia. Un 401 diria "esas credenciales no sirven", que durante el
     * enfriamiento seria mentira y ademas dejaria al cliente reintentando.
     * <p>
     * El mensaje si dice cuantos minutos quedan. No es una fuga: quien recibe
     * este 429 ya sabe que disparo el bloqueo, y sin el dato la unica forma de
     * averiguar cuando volver es seguir probando, que es lo contrario de lo
     * que se busca.
     */
    @ExceptionHandler(DemasiadosIntentosException.class)
    public ResponseEntity<ErrorResponse> manejarDemasiadosIntentos(
            DemasiadosIntentosException ex,
            HttpServletRequest request) {

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(HttpStatus.TOO_MANY_REQUESTS.value())
                .error("Demasiadas peticiones")
                .mensaje(ex.getMessage())
                .ruta(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(cuerpo);
    }

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
     * Un {@code @PreAuthorize} no se cumplió -> 403 FORBIDDEN.
     * <p>
     * Hace falta por donde viaja la excepción. {@code AccessDeniedException}
     * tiene dos orígenes y solo uno lo atiende Spring Security por su cuenta:
     * <ul>
     *   <li>La que nace en la cadena de filtros (la matriz de rutas de
     *       {@code SecurityConfig}) la atrapa el
     *       {@code ExceptionTranslationFilter} y la enruta al
     *       {@code AccesoDenegadoHandler}, que ya devuelve este mismo
     *       cuerpo.</li>
     *   <li>La que lanza method-security al evaluar un {@code @PreAuthorize}
     *       sale del método del controlador hacia arriba y la resuelve el
     *       DispatcherServlet, o sea esta clase. Sin este manejador caía en
     *       {@link #manejarExcepcionNoAnticipada} y el cliente recibía un 500
     *       "Error interno" por un fallo que es suyo y no del servidor.</li>
     * </ul>
     * El mensaje es el mismo del {@code AccesoDenegadoHandler} para que al
     * cliente le dé igual por cuál de los dos caminos se le negó el acceso, y
     * tampoco dice qué condición incumplió.
     * <p>
     * Nota para cuando se agreguen más {@code @PreAuthorize}: si alguna vez
     * una ruta queda {@code permitAll} en la matriz pero con
     * {@code @PreAuthorize} en el método, un cliente anónimo recibiría aquí un
     * 403 donde Spring Security habría respondido 401 invitando a
     * autenticarse. Hoy no ocurre: todo lo que lleva {@code @PreAuthorize}
     * está además detrás de {@code authenticated()} en la matriz.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> manejarAccesoDenegado(
            AccessDeniedException ex,
            HttpServletRequest request) {

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(HttpStatus.FORBIDDEN.value())
                .error("Prohibido")
                .mensaje("No tiene los permisos necesarios para realizar esta operación")
                .ruta(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
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
     * Parametro de paginacion fuera de rango -> 400 BAD REQUEST.
     */
    @ExceptionHandler(ParametroInvalidoException.class)
    public ResponseEntity<ErrorResponse> manejarParametroInvalido(
            ParametroInvalidoException ex,
            HttpServletRequest request) {

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(HttpStatus.BAD_REQUEST.value())
                .error("Parámetro inválido")
                .mensaje(ex.getMessage())
                .ruta(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(cuerpo);
    }

    /**
     * Token de recuperacion de clave invalido, usado o expirado -> 400 BAD REQUEST.
     */
    @ExceptionHandler(TokenInvalidoException.class)
    public ResponseEntity<ErrorResponse> manejarTokenInvalido(
            TokenInvalidoException ex,
            HttpServletRequest request) {

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(HttpStatus.BAD_REQUEST.value())
                .error("Token inválido")
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
         * Caso 0:
         * Numero sintacticamente valido pero fuera del rango del tipo destino.
         *
         * Ejemplo:
         * "idEspecie": 99999999999999999999
         *
         * QA observo (AN-H08) que esto respondia "El cuerpo de la solicitud no
         * tiene un formato JSON valido", culpando al JSON de un problema que
         * no era suyo: el JSON estaba bien formado, el valor no cabia en un
         * long. Va antes que los demas casos porque Jackson envuelve la
         * InputCoercionException dentro de una excepcion de databind que
         * tambien encaja en los instanceof de abajo.
         */
        InputCoercionException fueraDeRango = buscarFueraDeRango(causa);
        if (fueraDeRango != null) {

            String campo = causa instanceof DatabindException error
                    ? nombreDelCampo(error.getPath())
                    : null;
            if (campo == null) {
                campo = campoDelParser(fueraDeRango);
            }

            mensaje = campo != null
                    ? "El campo '" + campo + "' tiene un valor numérico fuera de rango"
                    : "La solicitud contiene un valor numérico fuera de rango";

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.builder()
                            .timestamp(LocalDateTime.now())
                            .estado(HttpStatus.BAD_REQUEST.value())
                            .error("Solicitud mal formada")
                            .mensaje(mensaje)
                            .ruta(request.getRequestURI())
                            .build());
        }

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

            if (esFechaUHora(tipo)) {
                /*
                 * Fecha en un formato que no es el del contrato.
                 *
                 * Ejemplos: "15/10/2026" o "2026-10-15T10:30:00" en
                 * fechaObservacion, que con @JsonFormat(pattern="yyyy-MM-dd")
                 * y lectura no leniente ya no se aceptan. Antes el mensaje
                 * decia solo "tipo de dato incorrecto", sin indicar el formato
                 * esperado (AN-H10).
                 */
                mensaje = campo != null
                        ? "El campo '" + campo + "' debe tener el formato yyyy-MM-dd"
                        : "La fecha debe tener el formato yyyy-MM-dd";
            } else if (tipo != null && tipo.isEnum()) {

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
     * Ruta sin controlador (por ejemplo {@code /api/habitats/} con barra
     * final) -> 404 NOT FOUND, sin exponer el stack trace.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> manejarRutaInexistente(
            NoResourceFoundException ex,
            HttpServletRequest request) {

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(HttpStatus.NOT_FOUND.value())
                .error("Recurso no encontrado")
                .mensaje("La ruta solicitada no existe")
                .ruta(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(cuerpo);
    }

    /**
     * Falta un {@code @RequestParam} obligatorio -> 400 BAD REQUEST.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> manejarParametroFaltante(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(HttpStatus.BAD_REQUEST.value())
                .error("Parámetro requerido")
                .mensaje("Falta el parámetro obligatorio '" + ex.getParameterName() + "'")
                .ruta(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(cuerpo);
    }

    /**
     * La ruta existe pero no admite ese verbo (por ejemplo
     * {@code DELETE /api/especies}, que solo esta mapeado como
     * {@code DELETE /api/especies/{id}}) -> 405 METHOD NOT ALLOWED.
     * <p>
     * La respuesta lleva la cabecera {@code Allow}, que informa al cliente de
     * que metodos si acepta esa ruta, para que pueda corregir la llamada sin
     * consultar la documentacion. Si la excepcion no trae la lista de metodos
     * soportados la cabecera va vacia, nunca con "null".
     * <p>
     * Va antes del catch-all por legibilidad, no por necesidad: Spring elige
     * el manejador por la clase mas especifica, no por el orden en el fichero.
     * Sin este manejador la excepcion caia en
     * {@link #manejarExcepcionNoAnticipada} y salia como 500 "Error interno",
     * que ademas de poco util apunta al servidor cuando el error es del
     * cliente.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> manejarMetodoNoSoportado(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(HttpStatus.METHOD_NOT_ALLOWED.value())
                .error("Método no permitido")
                .mensaje("El método " + ex.getMethod() + " no está soportado en esta ruta")
                .ruta(request.getRequestURI())
                .build();

        // HttpMethod dejo de ser enum en Spring 6, asi que el metodo de
        // referencia es HttpMethod::name y no Enum::name.
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .header(HttpHeaders.ALLOW, String.join(", ",
                        ex.getSupportedHttpMethods() == null
                                ? List.<String>of()
                                : ex.getSupportedHttpMethods().stream()
                                        .map(HttpMethod::name)
                                        .toList()))
                .body(cuerpo);
    }

    /**
     * El cuerpo llega con un Content-Type que la ruta no sabe leer (por
     * ejemplo {@code POST /api/especies/1/videos} con
     * {@code Content-Type: text/plain}) -> 415 UNSUPPORTED MEDIA TYPE.
     * <p>
     * Mismo caso que {@link #manejarMetodoNoSoportado}: sin manejador propio
     * la excepcion caia en {@link #manejarExcepcionNoAnticipada} y salia como
     * 500 "Error interno", culpando al servidor de un error del cliente. Lo
     * reporto QA en el 3er informe (modulo Video Especie, prueba del
     * Content-Type no soportado).
     * <p>
     * El mensaje enumera los tipos que la ruta si acepta para que el cliente
     * pueda corregir la llamada; si la excepcion no los trae, se nombra
     * {@code application/json}, que es el unico formato que consume la API.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> manejarMediaTypeNoSoportado(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {

        List<MediaType> soportados = ex.getSupportedMediaTypes();
        String admitidos = soportados == null || soportados.isEmpty()
                ? MediaType.APPLICATION_JSON_VALUE
                : soportados.stream().map(MediaType::toString).collect(Collectors.joining(", "));

        String recibido = ex.getContentType() != null
                ? "'" + ex.getContentType() + "'"
                : "el indicado";

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value())
                .error("Tipo de contenido no soportado")
                .mensaje("El tipo de contenido " + recibido
                        + " no está soportado en esta ruta. Tipos admitidos: " + admitidos)
                .ruta(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(cuerpo);
    }

    /**
     * Restricciones de Bean Validation sobre parametros de metodo
     * ({@code @Positive} en un {@code @PathVariable} de un controlador
     * {@code @Validated}) -> 400 BAD REQUEST.
     * <p>
     * Hermano de {@link #manejarErroresDeValidacion}, que solo atiende el
     * {@code @Valid} del cuerpo: las violaciones de parametros llegan como
     * {@link ConstraintViolationException} y sin este manejador acabarian en
     * el catch-all como 500.
     * <p>
     * La ruta de la violacion viene como "metodo.parametro"; al cliente solo
     * se le devuelve el ultimo tramo, que es el nombre del parametro.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> manejarViolacionesDeParametros(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        Map<String, String> erroresValidacion = new HashMap<>();

        for (ConstraintViolation<?> violacion : ex.getConstraintViolations()) {
            erroresValidacion.put(
                    ultimoTramo(violacion.getPropertyPath().toString()),
                    violacion.getMessage()
            );
        }

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(HttpStatus.BAD_REQUEST.value())
                .error("Error de validación")
                .mensaje("La solicitud contiene parámetros inválidos")
                .ruta(request.getRequestURI())
                .erroresValidacion(erroresValidacion)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cuerpo);
    }

    /**
     * Red de seguridad final: cualquier excepcion que no coincida con ninguno
     * de los manejadores anteriores -> 500 INTERNAL SERVER ERROR.
     * <p>
     * Su papel es exclusivamente ese: red de ultimo recurso para errores
     * <em>imprevistos</em>. Toda excepcion conocida debe tener su propio
     * manejador mas arriba, con el codigo y el mensaje que le corresponden; si
     * una excepcion previsible acaba aqui, el 500 resultante es un defecto de
     * esta clase y no un fallo del cliente. Fue el caso de
     * {@link HttpRequestMethodNotSupportedException}, que respondia 500 hasta
     * que se le dio su manejador propio ({@link #manejarMetodoNoSoportado}).
     * <p>
     * Es deliberadamente inespecifica: al cliente solo se le dice que algo
     * salio mal, nunca la clase de la excepcion ni su mensaje real (podria
     * filtrar detalles internos, como ya ocurre a proposito con
     * DataIntegrityViolationException). El stack trace completo si se escribe
     * en el log del servidor, que es donde hay que ir a diagnosticar un 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarExcepcionNoAnticipada(
            Exception ex,
            HttpServletRequest request) {

        log.error("Excepcion no anticipada en {} {}", request.getMethod(), request.getRequestURI(), ex);

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Error interno")
                .mensaje("Ocurrió un error inesperado. Si persiste, contacta al equipo de backend.")
                .ruta(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cuerpo);
    }

    /**
     * {@link ResponseStatusException} lanzada por algún servicio -> el código
     * que trae la propia excepción.
     * <p>
     * La convención del proyecto es lanzar sus propias excepciones
     * ({@link RecursoNoEncontradoException}, {@link RecursoDuplicadoException},
     * {@link EstadoInvalidoException}…), que ya tienen manejador aquí. Este
     * existe solo para que las {@code ResponseStatusException} que aún quedan
     * no pierdan su mensaje: sin él las atendería el controlador de errores de
     * Spring, que con {@code server.error.include-message=never} lo descarta y
     * además no responde en formato {@link ErrorResponse}.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> manejarResponseStatus(
            ResponseStatusException ex,
            HttpServletRequest request) {

        int codigo = ex.getStatusCode().value();
        String error = textoDelCodigo(codigo);
        String mensaje = ex.getReason() != null
                ? ex.getReason()
                : "La solicitud no pudo completarse (" + codigo + " " + error + ")";

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(codigo)
                .error(error)
                .mensaje(mensaje)
                .ruta(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(cuerpo);
    }

    /**
     * Texto en español de los códigos HTTP habituales; para el resto, la frase
     * estándar en inglés de {@link HttpStatus}.
     */
    private static String textoDelCodigo(int codigo) {
        return switch (codigo) {
            case 400 -> "Solicitud incorrecta";
            case 401 -> "No autorizado";
            case 403 -> "Prohibido";
            case 404 -> "Recurso no encontrado";
            case 405 -> "Método no permitido";
            case 409 -> "Conflicto";
            case 422 -> "Entidad no procesable";
            case 500 -> "Error interno del servidor";
            case 503 -> "Servicio no disponible";
            default -> {
                HttpStatus estado = HttpStatus.resolve(codigo);
                yield estado != null ? estado.getReasonPhrase() : "Error " + codigo;
            }
        };
    }

    /**
     * Recorre la cadena de causas buscando el desbordamiento numerico.
     * <p>
     * Jackson lanza la {@link InputCoercionException} en la capa de parseo y
     * la capa de databind la envuelve para poder adjuntar la ruta del campo,
     * asi que no basta con mirar la causa directa.
     */
    private static InputCoercionException buscarFueraDeRango(Throwable causa) {
        for (Throwable actual = causa; actual != null; actual = actual.getCause()) {
            if (actual instanceof InputCoercionException error) {
                return error;
            }
        }
        return null;
    }

    /**
     * Nombre del campo que estaba leyendo el parser cuando desbordo.
     * <p>
     * La {@link InputCoercionException} la lanza la capa de parseo, que no
     * conoce el objeto que se esta poblando, asi que no trae la ruta de
     * propiedades que si traen las excepciones de databind. Lo que si trae es
     * el propio parser, detenido sobre el valor culpable: su campo actual es
     * el que hay que nombrarle al cliente.
     */
    private static String campoDelParser(InputCoercionException ex) {
        if (!(ex.processor() instanceof JsonParser parser)) {
            return null;
        }
        try {
            return parser.currentName();
        } catch (Exception e) {
            // El parser ya puede estar cerrado; el mensaje generico sirve igual.
            return null;
        }
    }

    /** Tipos de fecha/hora de java.time que la API recibe en los cuerpos. */
    private static boolean esFechaUHora(Class<?> tipo) {
        return tipo == LocalDate.class
                || tipo == LocalDateTime.class
                || tipo == LocalTime.class;
    }

    /**
     * Ultimo tramo de una ruta de propiedad separada por puntos; para
     * "crear.idPreparacion" devuelve "idPreparacion".
     */
    private static String ultimoTramo(String rutaDePropiedad) {
        int ultimoPunto = rutaDePropiedad.lastIndexOf('.');
        return ultimoPunto < 0 ? rutaDePropiedad : rutaDePropiedad.substring(ultimoPunto + 1);
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
