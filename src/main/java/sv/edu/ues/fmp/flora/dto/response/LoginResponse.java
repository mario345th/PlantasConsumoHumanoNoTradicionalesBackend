package sv.edu.ues.fmp.flora.dto.response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Respuesta de {@code POST /api/auth/login}.
 * <p>
 * {@code usuario} es el mismo {@link UsuarioResponse} que ya devolvia
 * {@code POST /api/usuarios/login}, que no incluye {@code claveHash} ni
 * ningun otro dato sensible.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {

    /** El JWT firmado. Va en la cabecera {@code Authorization: Bearer <token>}. */
    private String token;

    /** Siempre "Bearer". Se devuelve explicito para que el cliente arme la cabecera sin suponerlo. */
    private String tipo;

    /**
     * Momento en que el token deja de servir, en la zona horaria del
     * servidor. Es el mismo instante que lleva el claim {@code exp}, solo que
     * con el formato de fecha del resto de la API en vez de en segundos desde
     * epoch.
     */
    @JsonFormat(pattern = "dd/MM/yyyy HH:mm:ss")
    private LocalDateTime expiraEn;

    private UsuarioResponse usuario;
}
