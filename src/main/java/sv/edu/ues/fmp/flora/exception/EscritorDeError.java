package sv.edu.ues.fmp.flora.exception;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escribe un {@link ErrorResponse} directamente en la respuesta HTTP.
 * <p>
 * Lo necesitan {@link AutenticacionEntryPoint} y {@link AccesoDenegadoHandler}:
 * esos dos corren dentro de la cadena de filtros de Spring Security, antes de
 * que la peticion llegue al DispatcherServlet, asi que el
 * {@link GlobalExceptionHandler} nunca los ve y el cuerpo del 401 y del 403
 * hay que serializarlo a mano. Sin esto, Spring Security devuelve esos dos
 * codigos con cuerpo vacio y el cliente recibe un formato de error distinto al
 * del resto de la API.
 */
final class EscritorDeError {

    private EscritorDeError() {
    }

    static void escribir(
            JsonMapper jsonMapper,
            HttpServletRequest peticion,
            HttpServletResponse respuesta,
            HttpStatus estado,
            String error,
            String mensaje) throws IOException {

        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .estado(estado.value())
                .error(error)
                .mensaje(mensaje)
                .ruta(peticion.getRequestURI())
                .build();

        respuesta.setStatus(estado.value());
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(respuesta.getWriter(), cuerpo);
    }
}
