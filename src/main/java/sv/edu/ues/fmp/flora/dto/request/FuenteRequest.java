package sv.edu.ues.fmp.flora.dto.request;

import java.time.Year;
import java.util.regex.Pattern;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sv.edu.ues.fmp.flora.entity.enums.TipoFuente;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FuenteRequest {
    // lo que entra


    private static final Pattern URL_VALIDA =
            Pattern.compile("^https?://([\\w-]+\\.)+[a-zA-Z]{2,}(:\\d{1,5})?(/[^\\s]*)?$");

    @NotBlank(message = "El título es obligatorio") // (null,""," ")
    @Size(max = 300, message = "El título no puede exceder 300 caracteres")
    private String titulo;

    @Size(max = 250, message = "El autor no puede exceder 250 caracteres")
    private String autor;

    private Short anio;

    @NotNull(message = "El tipo de fuente es obligatorio")
    private TipoFuente tipoFuente;

    @Size(max = 1000, message = "La url no puede exceder 1000 caracteres")
    private String url;

    private String referenciaBibliografica;

    @AssertTrue(message = "Si el tipo de fuente es SITIO_WEB, la url es obligatoria "
            + "y debe tener un formato válido (http:// o https://)")
    public boolean isUrlValidaParaSitioWeb() {
        if (tipoFuente != TipoFuente.SITIO_WEB) {
            return true;
        }
        return url != null && URL_VALIDA.matcher(url.trim()).matches();
    }

    @AssertTrue(message = "El año debe ser un valor positivo y no mayor al año actual")
    public boolean isAnioValido() {
        if (anio == null) {
            return true;
        }
        int actual = Year.now().getValue();
        return anio >= 1 && anio <= actual;
    }
}