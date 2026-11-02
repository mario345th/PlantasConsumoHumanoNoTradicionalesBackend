package sv.edu.ues.fmp.flora.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import sv.edu.ues.fmp.flora.dto.request.VideoEspecieRequest;
import sv.edu.ues.fmp.flora.dto.request.VideoPreparacionRequest;
import sv.edu.ues.fmp.flora.entity.Especie;
import sv.edu.ues.fmp.flora.entity.PreparacionConsumo;
import sv.edu.ues.fmp.flora.entity.VideoEspecie;
import sv.edu.ues.fmp.flora.entity.VideoPreparacion;
import sv.edu.ues.fmp.flora.exception.EstadoInvalidoException;
import sv.edu.ues.fmp.flora.exception.RecursoDuplicadoException;
import sv.edu.ues.fmp.flora.mapper.VideoEspecieMapper;
import sv.edu.ues.fmp.flora.mapper.VideoPreparacionMapper;
import sv.edu.ues.fmp.flora.repository.EspecieParteComestibleRepository;
import sv.edu.ues.fmp.flora.repository.EspecieRepository;
import sv.edu.ues.fmp.flora.repository.PreparacionConsumoRepository;
import sv.edu.ues.fmp.flora.repository.VideoEspecieRepository;
import sv.edu.ues.fmp.flora.repository.VideoPreparacionRepository;
import sv.edu.ues.fmp.flora.service.impl.VideoEspecieServiceImpl;
import sv.edu.ues.fmp.flora.service.impl.VideoPreparacionServiceImpl;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de negocio de los dos modulos de video exigidas por el 3er reporte de
 * QA: VP-H03 / VE-H03 (URL duplicada), VP-H05 / VE-H01 (editar un registro
 * desactivado) y VP-H07 (desactivar o activar dos veces sin informar).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VideoServiceHallazgosTest {

    private static final String URL = "https://www.youtube.com/watch?v=chipilin01";

    @Mock
    private VideoPreparacionRepository videoPreparacionRepository;

    @Mock
    private PreparacionConsumoRepository preparacionConsumoRepository;

    @Mock
    private VideoPreparacionMapper videoPreparacionMapper;

    @InjectMocks
    private VideoPreparacionServiceImpl videoPreparacionService;

    @Mock
    private VideoEspecieRepository videoEspecieRepository;

    @Mock
    private EspecieRepository especieRepository;

    @Mock
    private EspecieParteComestibleRepository especieParteComestibleRepository;

    @Mock
    private VideoEspecieMapper videoEspecieMapper;

    @InjectMocks
    private VideoEspecieServiceImpl videoEspecieService;

    // ---------- VP-H05 / VE-H01: editar un video desactivado ----------

    @Test
    void preparacion_actualizarVideoDesactivado_lanzaEstadoInvalido() {
        VideoPreparacion video = videoPreparacion(11L, false);
        when(videoPreparacionRepository.findById(11L)).thenReturn(Optional.of(video));

        EstadoInvalidoException ex = assertThrows(EstadoInvalidoException.class,
                () -> videoPreparacionService.actualizar(11L, requestPreparacion()));

        assertFalse(ex.getMessage().isBlank(), "El 409 debe explicar que el video está desactivado");
        verify(videoPreparacionMapper, never()).updateEntity(any(), any());
    }

    @Test
    void especie_actualizarVideoDesactivado_lanzaEstadoInvalido() {
        VideoEspecie video = videoEspecie(2L, false);
        when(videoEspecieRepository.findById(2L)).thenReturn(Optional.of(video));
        when(especieRepository.findById(1L)).thenReturn(Optional.of(video.getEspecie()));

        assertThrows(EstadoInvalidoException.class,
                () -> videoEspecieService.actualizar(2L, requestEspecie()));

        verify(videoEspecieMapper, never()).updateEntity(any(), any(), any());
    }

    // ---------- VP-H03 / VE-H03: URL duplicada ----------

    @Test
    void preparacion_crearConUrlRepetida_lanzaRecursoDuplicado() {
        PreparacionConsumo preparacion = preparacion(true);
        when(preparacionConsumoRepository.findById(1L)).thenReturn(Optional.of(preparacion));
        when(videoPreparacionMapper.toEntity(any(), any())).thenReturn(videoPreparacion(null, true));
        when(videoPreparacionRepository
                .existsByPreparacionIdPreparacionAndUrlVideoAndActivoTrue(1L, URL)).thenReturn(true);

        assertThrows(RecursoDuplicadoException.class,
                () -> videoPreparacionService.crear(1L, requestPreparacion()));

        verify(videoPreparacionRepository, never()).save(any());
    }

    @Test
    void preparacion_actualizarTomandoLaUrlDeOtroVideo_lanzaRecursoDuplicado() {
        VideoPreparacion video = videoPreparacion(12L, true);
        when(videoPreparacionRepository.findById(12L)).thenReturn(Optional.of(video));
        when(videoPreparacionRepository
                .existsByPreparacionIdPreparacionAndUrlVideoAndActivoTrueAndIdVideoPreparacionNot(1L, URL, 12L))
                .thenReturn(true);

        assertThrows(RecursoDuplicadoException.class,
                () -> videoPreparacionService.actualizar(12L, requestPreparacion()));

        verify(videoPreparacionMapper, never()).updateEntity(any(), any());
    }

    @Test
    void preparacion_actualizarReenviandoSuPropiaUrl_noChocaConsigoMismo() {
        VideoPreparacion video = videoPreparacion(11L, true);
        when(videoPreparacionRepository.findById(11L)).thenReturn(Optional.of(video));
        // El exists excluye el propio id, asi que no encuentra duplicado.
        when(videoPreparacionRepository
                .existsByPreparacionIdPreparacionAndUrlVideoAndActivoTrueAndIdVideoPreparacionNot(1L, URL, 11L))
                .thenReturn(false);

        assertDoesNotThrow(() -> videoPreparacionService.actualizar(11L, requestPreparacion()));

        verify(videoPreparacionMapper).updateEntity(eq(video), any(VideoPreparacionRequest.class));
    }

    @Test
    void especie_crearConUrlRepetida_lanzaRecursoDuplicado() {
        Especie especie = especie(true);
        when(especieRepository.findById(1L)).thenReturn(Optional.of(especie));
        when(videoEspecieMapper.toEntity(any(), any(), any())).thenReturn(videoEspecie(null, true));
        when(videoEspecieRepository
                .existsByEspecieIdEspecieAndUrlVideoAndActivoTrue(1L, URL)).thenReturn(true);

        assertThrows(RecursoDuplicadoException.class,
                () -> videoEspecieService.crear(1L, requestEspecie()));

        verify(videoEspecieRepository, never()).save(any());
    }

    @Test
    void especie_actualizarTomandoLaUrlDeOtroVideo_lanzaRecursoDuplicado() {
        VideoEspecie video = videoEspecie(3L, true);
        when(videoEspecieRepository.findById(3L)).thenReturn(Optional.of(video));
        when(especieRepository.findById(1L)).thenReturn(Optional.of(video.getEspecie()));
        when(videoEspecieRepository
                .existsByEspecieIdEspecieAndUrlVideoAndActivoTrueAndIdVideoNot(1L, URL, 3L)).thenReturn(true);

        assertThrows(RecursoDuplicadoException.class,
                () -> videoEspecieService.actualizar(3L, requestEspecie()));

        verify(videoEspecieMapper, never()).updateEntity(any(), any(), any());
    }

    // ---------- VP-H07: idempotencia que no informa ----------

    @Test
    void preparacion_desactivarVideoYaDesactivado_lanzaEstadoInvalido() {
        when(videoPreparacionRepository.findById(14L))
                .thenReturn(Optional.of(videoPreparacion(14L, false)));

        assertThrows(EstadoInvalidoException.class, () -> videoPreparacionService.desactivar(14L));
    }

    @Test
    void preparacion_activarVideoYaActivo_lanzaEstadoInvalido() {
        when(videoPreparacionRepository.findById(11L))
                .thenReturn(Optional.of(videoPreparacion(11L, true)));

        assertThrows(EstadoInvalidoException.class, () -> videoPreparacionService.activar(11L));
    }

    @Test
    void especie_desactivarVideoYaDesactivado_lanzaEstadoInvalido() {
        when(videoEspecieRepository.findById(2L)).thenReturn(Optional.of(videoEspecie(2L, false)));

        assertThrows(EstadoInvalidoException.class, () -> videoEspecieService.desactivar(2L));
    }

    @Test
    void especie_activarVideoYaActivo_lanzaEstadoInvalido() {
        when(videoEspecieRepository.findById(1L)).thenReturn(Optional.of(videoEspecie(1L, true)));

        assertThrows(EstadoInvalidoException.class, () -> videoEspecieService.activar(1L));
    }

    // ---------- datos de apoyo ----------

    private static PreparacionConsumo preparacion(boolean activa) {
        return PreparacionConsumo.builder()
                .idPreparacion(1L)
                .nombre("Sopa de chipilín")
                .activa(activa)
                .build();
    }

    private static Especie especie(boolean activa) {
        return Especie.builder()
                .idEspecie(1L)
                .nombreCientifico("Crotalaria longirostrata")
                .activa(activa)
                .build();
    }

    private static VideoPreparacion videoPreparacion(Long id, boolean activo) {
        return VideoPreparacion.builder()
                .idVideoPreparacion(id)
                .preparacion(preparacion(true))
                .titulo("Cultivo tradicional de Chipilín")
                .urlVideo(URL)
                .activo(activo)
                .build();
    }

    private static VideoEspecie videoEspecie(Long id, boolean activo) {
        return VideoEspecie.builder()
                .idVideo(id)
                .especie(especie(true))
                .titulo("Cultivo tradicional de Chipilín")
                .urlVideo(URL)
                .activo(activo)
                .build();
    }

    private static VideoPreparacionRequest requestPreparacion() {
        return VideoPreparacionRequest.builder()
                .titulo("Cultivo tradicional de Chipilín")
                .urlVideo(URL)
                .build();
    }

    private static VideoEspecieRequest requestEspecie() {
        return VideoEspecieRequest.builder()
                .titulo("Cultivo tradicional de Chipilín")
                .urlVideo(URL)
                .build();
    }
}
