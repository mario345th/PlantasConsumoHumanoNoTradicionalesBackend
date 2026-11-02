package sv.edu.ues.fmp.flora.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import sv.edu.ues.fmp.flora.config.JacksonConfig;
import sv.edu.ues.fmp.flora.dto.response.UbicacionEspecieResponse;
import sv.edu.ues.fmp.flora.exception.GlobalExceptionHandler;
import sv.edu.ues.fmp.flora.service.UbicacionEspecieService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lectura del cuerpo de /api/ubicaciones, de extremo a extremo.
 * <p>
 * Cubre los hallazgos que no se ven desde el DTO porque dependen de Jackson y
 * del manejador global: AN-H08 (numero fuera de rango que se reportaba como
 * "JSON no valido"), AN-H09 (coordenadas aceptadas como texto) y AN-H10
 * (formato de fecha).
 */
@WebMvcTest(UbicacionEspecieController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ GlobalExceptionHandler.class, JacksonConfig.class })
class UbicacionEspecieCuerpoTest {

    private static final String RUTA = "/api/ubicaciones";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UbicacionEspecieService ubicacionEspecieService;

    // ---------- AN-H08: numero fuera del rango de Long ----------

    @Test
    void idEspecieMayorQueLong_devuelve400NombrandoElCampo() throws Exception {
        String cuerpo = """
                {
                  "idEspecie": 99999999999999999999,
                  "idDistrito": 1,
                  "nombreLugar": "Cerro Las Pavas",
                  "latitud": 13.6417,
                  "longitud": -88.7850
                }
                """;

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400))
                // Ya no debe culpar al JSON, que estaba bien formado.
                .andExpect(jsonPath("$.mensaje").value(
                        org.hamcrest.Matchers.containsString("fuera de rango")))
                .andExpect(jsonPath("$.mensaje").value(
                        org.hamcrest.Matchers.containsString("idEspecie")));

        verify(ubicacionEspecieService, never()).crear(any());
    }

    // ---------- AN-H09: coordenadas como texto ----------

    @Test
    void latitudComoTexto_devuelve400IgualQueIdEspecie() throws Exception {
        String cuerpo = """
                {
                  "idEspecie": 1,
                  "idDistrito": 1,
                  "nombreLugar": "Cerro Las Pavas",
                  "latitud": "13.6417",
                  "longitud": -88.7850
                }
                """;

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400));

        verify(ubicacionEspecieService, never()).crear(any());
    }

    @Test
    void latitudComoEnteroSigueSiendoValida() throws Exception {
        // "latitud": 14 representa exactamente 14.0; no es una coercion dudosa.
        String cuerpo = """
                {
                  "idEspecie": 1,
                  "idDistrito": 1,
                  "nombreLugar": "Cerro Las Pavas",
                  "latitud": 14,
                  "longitud": -88.7850
                }
                """;

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated());
    }

    // ---------- AN-H10: formato de fecha ----------

    @Test
    void fechaConHora_devuelve400IndicandoElFormato() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoConFecha("2026-10-15T10:30:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(
                        org.hamcrest.Matchers.containsString("yyyy-MM-dd")));

        verify(ubicacionEspecieService, never()).crear(any());
    }

    @Test
    void fechaEnFormatoSalvadoreno_devuelve400IndicandoElFormato() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoConFecha("15/10/2026")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(
                        org.hamcrest.Matchers.containsString("yyyy-MM-dd")));
    }

    @Test
    void fechaEnElFormatoDelContrato_esAceptada() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoConFecha("2026-10-15")))
                .andExpect(status().isCreated());
    }

    // ---------- reactivar: cableado de la ruta PATCH ----------

    /**
     * Las reglas de la reactivacion se prueban en el servicio; aqui solo se
     * comprueba que la ruta PATCH exista y devuelva 200 con el DTO del modulo,
     * igual que el resto de los /{id}/activar del proyecto.
     */
    @Test
    void reactivar_respondeOkConElEstadoNuevo() throws Exception {
        when(ubicacionEspecieService.reactivarUbicacion(30L))
                .thenReturn(UbicacionEspecieResponse.builder()
                        .idUbicacion(30L)
                        .idEspecie(1L)
                        .activa(true)
                        .build());

        mockMvc.perform(patch(RUTA + "/30/activar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idUbicacion").value(30))
                .andExpect(jsonPath("$.activa").value(true));
    }

    private static String cuerpoConFecha(String fechaObservacion) {
        return """
                {
                  "idEspecie": 1,
                  "idDistrito": 1,
                  "nombreLugar": "Cerro Las Pavas",
                  "latitud": 13.6417,
                  "longitud": -88.7850,
                  "fechaObservacion": "%s"
                }
                """.formatted(fechaObservacion);
    }
}
