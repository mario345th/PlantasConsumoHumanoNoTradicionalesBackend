package sv.edu.ues.fmp.flora.exception;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * 403 para la peticion que si trae un token valido pero cuyas autoridades no
 * alcanzan para la operacion (el caso de un {@code @PreAuthorize} que no se
 * cumple).
 * <p>
 * Tampoco dice que rol o permiso faltaba: eso es un mapa de la estructura de
 * autorizacion para quien este tanteando. "Prohibido" es el mismo texto que
 * usa {@code GlobalExceptionHandler.textoDelCodigo(403)}.
 */
@Component
@RequiredArgsConstructor
public class AccesoDenegadoHandler implements AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    @Override
    public void handle(
            HttpServletRequest peticion,
            HttpServletResponse respuesta,
            AccessDeniedException excepcion) throws IOException {

        EscritorDeError.escribir(
                jsonMapper,
                peticion,
                respuesta,
                HttpStatus.FORBIDDEN,
                "Prohibido",
                "No tiene los permisos necesarios para realizar esta operación");
    }
}
