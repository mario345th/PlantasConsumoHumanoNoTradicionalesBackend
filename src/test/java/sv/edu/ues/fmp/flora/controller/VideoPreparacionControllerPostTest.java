package sv.edu.ues.fmp.flora.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import sv.edu.ues.fmp.flora.dto.request.VideoPreparacionRequest;
import sv.edu.ues.fmp.flora.dto.response.VideoPreparacionResponse;
import sv.edu.ues.fmp.flora.exception.GlobalExceptionHandler;
import sv.edu.ues.fmp.flora.service.VideoPreparacionService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * VP-H01: el POST que faltaba en /api/videos-preparacion, de extremo a extremo.
 * <p>
 * Comprueba ademas los dos codigos que el 3er reporte de QA esperaba y no
 * obtenia: 415 cuando el Content-Type no es JSON (el defecto que daba 500) y
 * 400 cuando el id del path no es positivo.
 */
@WebMvcTest(VideoPreparacionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class VideoPreparacionControllerPostTest {

    private static final String RUTA = "/api/videos-preparacion/preparacion/{idPreparacion}";

    private static final String CUERPO_VALIDO = """
            {
              "titulo": "Elaboración paso a paso de la receta",
              "urlVideo": "https://www.youtube.com/watch?v=chipilin01",
              "duracionSegundos": 180
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VideoPreparacionService videoPreparacionService;

    @Test
    void post_conDatosValidos_devuelve201ConLocation() throws Exception {
        when(videoPreparacionService.crear(eq(1L), any(VideoPreparacionRequest.class)))
                .thenReturn(VideoPreparacionResponse.builder()
                        .idVideoPreparacion(15L)
                        .idPreparacion(1L)
                        .titulo("Elaboración paso a paso de la receta")
                        .urlVideo("https://www.youtube.com/watch?v=chipilin01")
                        .activo(true)
                        .build());

        mockMvc.perform(post(RUTA, 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/videos-preparacion/15"))
                .andExpect(jsonPath("$.idVideoPreparacion").value(15));
    }

    @Test
    void post_conContentTypeTextPlain_devuelve415YNo500() throws Exception {
        mockMvc.perform(post(RUTA, 1)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.estado").value(415))
                .andExpect(jsonPath("$.ruta").value("/api/videos-preparacion/preparacion/1"));

        verify(videoPreparacionService, never()).crear(any(), any());
    }

    @Test
    void post_conIdDePreparacionNoPositivo_devuelve400YNo500() throws Exception {
        mockMvc.perform(post(RUTA, 0)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400));

        verify(videoPreparacionService, never()).crear(any(), any());
    }

    @Test
    void post_conTituloConScript_devuelve400() throws Exception {
        String cuerpoConXss = """
                {
                  "titulo": "<script>alert('xss')</script>",
                  "urlVideo": "https://www.youtube.com/watch?v=chipilin01"
                }
                """;

        mockMvc.perform(post(RUTA, 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoConXss))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400))
                .andExpect(jsonPath("$.erroresValidacion.titulo").exists());

        verify(videoPreparacionService, never()).crear(any(), any());
    }

    @Test
    void post_conUrlSinDominio_devuelve400() throws Exception {
        String cuerpoUrlLaxa = """
                {
                  "titulo": "Elaboración paso a paso de la receta",
                  "urlVideo": "https://a"
                }
                """;

        mockMvc.perform(post(RUTA, 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoUrlLaxa))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erroresValidacion.urlVideo").exists());

        verify(videoPreparacionService, never()).crear(any(), any());
    }
}
