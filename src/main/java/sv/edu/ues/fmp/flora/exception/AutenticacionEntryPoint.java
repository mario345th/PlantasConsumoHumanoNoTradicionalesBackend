package sv.edu.ues.fmp.flora.exception;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * 401 para la peticion que llega sin token, con un token que no verifica
 * firma, o con uno ya expirado.
 * <p>
 * El mensaje es deliberadamente generico y siempre el mismo. Distinguir "falta
 * el token" de "la firma no cuadra" de "expiro hace dos minutos" le diria a
 * quien esta probando tokens a ciegas cual de sus intentos va por buen camino.
 * El detalle real de por que fallo la verificacion si queda en el log de
 * Spring Security, que es donde hay que ir a diagnosticarlo.
 */
@Component
@RequiredArgsConstructor
public class AutenticacionEntryPoint implements AuthenticationEntryPoint {

    private final JsonMapper jsonMapper;

    @Override
    public void commence(
            HttpServletRequest peticion,
            HttpServletResponse respuesta,
            AuthenticationException excepcion) throws IOException {

        EscritorDeError.escribir(
                jsonMapper,
                peticion,
                respuesta,
                HttpStatus.UNAUTHORIZED,
                "No autorizado",
                "Se requiere un token de acceso válido para esta operación");
    }
}
