package sv.edu.ues.fmp.flora.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sv.edu.ues.fmp.flora.entity.Especie;
import sv.edu.ues.fmp.flora.entity.ImagenEspecie;
import sv.edu.ues.fmp.flora.entity.ImagenPreparacion;
import sv.edu.ues.fmp.flora.entity.PreparacionConsumo;
import sv.edu.ues.fmp.flora.exception.EstadoInvalidoException;
import sv.edu.ues.fmp.flora.mapper.ImagenEspecieMapper;
import sv.edu.ues.fmp.flora.mapper.ImagenPreparacionMapper;
import sv.edu.ues.fmp.flora.repository.EspecieRepository;
import sv.edu.ues.fmp.flora.repository.ImagenEspecieRepository;
import sv.edu.ues.fmp.flora.repository.ImagenPreparacionRepository;
import sv.edu.ues.fmp.flora.repository.PreparacionConsumoRepository;
import sv.edu.ues.fmp.flora.service.impl.ImagenEspecieServiceImpl;
import sv.edu.ues.fmp.flora.service.impl.ImagenPreparacionServiceImpl;
import sv.edu.ues.fmp.flora.service.impl.ValidadorParteEspecie;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * IM-H01: con el padre inactivo el sistema ya bloqueaba el resto de cambios
 * sobre sus imagenes, pero dejaba volver a marcar como principal una imagen
 * que ya lo era. Ahora marcar-principal se rechaza igual que las demas.
 */
@ExtendWith(MockitoExtension.class)
class ImagenMarcarPrincipalHallazgoTest {

    @Mock
    private ImagenEspecieRepository imagenEspecieRepository;

    @Mock
    private EspecieRepository especieRepository;

    @Mock
    private ValidadorParteEspecie validadorParteEspecie;

    @Mock
    private ImagenEspecieMapper imagenEspecieMapper;

    @InjectMocks
    private ImagenEspecieServiceImpl imagenEspecieService;

    @Mock
    private ImagenPreparacionRepository imagenPreparacionRepository;

    @Mock
    private PreparacionConsumoRepository preparacionConsumoRepository;

    @Mock
    private ImagenPreparacionMapper imagenPreparacionMapper;

    @InjectMocks
    private ImagenPreparacionServiceImpl imagenPreparacionService;

    @Test
    void especieInactiva_marcarPrincipalUnaImagenQueYaEraPrincipal_lanzaEstadoInvalido() {
        Especie especie = Especie.builder()
                .idEspecie(10L)
                .nombreCientifico("Crotalaria longirostrata")
                .activa(false)
                .build();
        ImagenEspecie imagen = ImagenEspecie.builder()
                .idImagen(1L)
                .especie(especie)
                .activa(true)
                .esPrincipal(true)
                .build();
        when(imagenEspecieRepository.findById(1L)).thenReturn(Optional.of(imagen));

        EstadoInvalidoException ex = assertThrows(EstadoInvalidoException.class,
                () -> imagenEspecieService.marcarComoPrincipal(1L));

        assertTrue(ex.getMessage().contains("desactivada"),
                "El mensaje debe señalar que la especie está desactivada");
        assertTrue(imagen.getEsPrincipal(), "La imagen conserva su estado: la operación no se aplicó");
    }

    @Test
    void preparacionInactiva_marcarPrincipalUnaImagenQueYaEraPrincipal_lanzaEstadoInvalido() {
        PreparacionConsumo preparacion = PreparacionConsumo.builder()
                .idPreparacion(20L)
                .nombre("Sopa de chipilín")
                .activa(false)
                .build();
        ImagenPreparacion imagen = ImagenPreparacion.builder()
                .idImagenPreparacion(2L)
                .preparacion(preparacion)
                .activa(true)
                .esPrincipal(true)
                .build();
        when(imagenPreparacionRepository.findById(2L)).thenReturn(Optional.of(imagen));

        EstadoInvalidoException ex = assertThrows(EstadoInvalidoException.class,
                () -> imagenPreparacionService.marcarComoPrincipal(2L));

        assertTrue(ex.getMessage().contains("desactivada"),
                "El mensaje debe señalar que la preparación está desactivada");
        assertTrue(imagen.getEsPrincipal(), "La imagen conserva su estado: la operación no se aplicó");
    }

    @Test
    void especieActiva_marcarPrincipalSigueSiendoIdempotente() {
        Especie especie = Especie.builder()
                .idEspecie(10L)
                .nombreCientifico("Crotalaria longirostrata")
                .activa(true)
                .build();
        ImagenEspecie imagen = ImagenEspecie.builder()
                .idImagen(1L)
                .especie(especie)
                .activa(true)
                .esPrincipal(true)
                .build();
        when(imagenEspecieRepository.findById(1L)).thenReturn(Optional.of(imagen));

        imagenEspecieService.marcarComoPrincipal(1L);

        assertTrue(imagen.getEsPrincipal(), "Con la especie activa repetir la promoción no cambia nada");
    }
}
