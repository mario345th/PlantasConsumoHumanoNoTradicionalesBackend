package sv.edu.ues.fmp.flora.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import sv.edu.ues.fmp.flora.dto.request.UbicacionEspecieRequest;
import sv.edu.ues.fmp.flora.dto.response.UbicacionEspecieResponse;
import sv.edu.ues.fmp.flora.entity.Distrito;
import sv.edu.ues.fmp.flora.entity.Especie;
import sv.edu.ues.fmp.flora.entity.UbicacionEspecie;
import sv.edu.ues.fmp.flora.exception.EstadoInvalidoException;
import sv.edu.ues.fmp.flora.exception.RecursoDuplicadoException;
import sv.edu.ues.fmp.flora.exception.RecursoNoEncontradoException;
import sv.edu.ues.fmp.flora.repository.DistritoRepository;
import sv.edu.ues.fmp.flora.repository.EspecieRepository;
import sv.edu.ues.fmp.flora.repository.MunicipioRepository;
import sv.edu.ues.fmp.flora.repository.MunicipioRepository.DepartamentoDeMunicipio;
import sv.edu.ues.fmp.flora.repository.UbicacionEspecieRepository;
import sv.edu.ues.fmp.flora.service.impl.UbicacionEspecieServiceImpl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de negocio de Ubicacion de Especie exigidas por el 3er reporte de QA:
 * AN-H03 (editar una desactivada), AN-H05 (departamento en null),
 * AN-H06 (duplicado de coordenadas) y AN-H13 (desactivar dos veces).
 * <p>
 * Cubre tambien la reactivacion, que comparte la regla de AN-H06: el indice
 * parcial de V11 solo admite una ubicacion activa por coordenada, asi que
 * revertir una baja puede chocar con otra fila que ocupo ese sitio.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UbicacionEspecieHallazgosTest {

    private static final BigDecimal LATITUD = new BigDecimal("13.6417");
    private static final BigDecimal LONGITUD = new BigDecimal("-88.7850");

    @Mock
    private UbicacionEspecieRepository ubicacionEspecieRepository;

    @Mock
    private EspecieRepository especieRepository;

    @Mock
    private DistritoRepository distritoRepository;

    @Mock
    private MunicipioRepository municipioRepository;

    @InjectMocks
    private UbicacionEspecieServiceImpl service;

    // ---------- AN-H03: editar una ubicacion desactivada ----------

    @Test
    void actualizarUbicacionDesactivada_lanzaEstadoInvalido() {
        when(ubicacionEspecieRepository.findById(125L))
                .thenReturn(Optional.of(ubicacion(125L, false)));

        EstadoInvalidoException ex = assertThrows(EstadoInvalidoException.class,
                () -> service.actualizarUbicacion(125L, request()));

        assertTrue(ex.getMessage().contains("desactivada"),
                "El 409 debe decir que la ubicación está desactivada");
        verify(ubicacionEspecieRepository, never()).save(any());
    }

    // ---------- AN-H13: desactivar dos veces ----------

    @Test
    void desactivarUbicacionYaDesactivada_lanzaEstadoInvalido() {
        when(ubicacionEspecieRepository.findById(124L))
                .thenReturn(Optional.of(ubicacion(124L, false)));

        EstadoInvalidoException ex = assertThrows(EstadoInvalidoException.class,
                () -> service.desactivarUbicacion(124L));

        assertTrue(ex.getMessage().contains("ya está desactivada"),
                "Repetir la baja debe informar que no cambió nada");
    }

    @Test
    void desactivarUbicacionActiva_laDaDeBaja() {
        UbicacionEspecie ubicacion = ubicacion(10L, true);
        when(ubicacionEspecieRepository.findById(10L)).thenReturn(Optional.of(ubicacion));

        service.desactivarUbicacion(10L);

        assertEquals(false, ubicacion.getActiva(), "La primera baja sí debe aplicarse");
    }

    // ---------- reactivar: revertir la baja respetando el índice parcial de V11 ----------

    @Test
    void reactivarUbicacionDesactivada_laVuelveAActivar() {
        UbicacionEspecie ubicacion = ubicacion(30L, false);
        when(ubicacionEspecieRepository.findById(30L)).thenReturn(Optional.of(ubicacion));
        when(ubicacionEspecieRepository.saveAndFlush(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(municipioRepository.findDepartamentosPorMunicipio(anyCollection()))
                .thenReturn(List.of(departamentoDeMunicipio(70L, "San Vicente")));

        UbicacionEspecieResponse response = service.reactivarUbicacion(30L);

        assertEquals(true, ubicacion.getActiva(), "La reactivación debe aplicarse");
        assertEquals(true, response.getActiva(), "La respuesta debe reflejar el nuevo estado");
        verify(ubicacionEspecieRepository).saveAndFlush(ubicacion);
    }

    @Test
    void reactivarCuandoOtraOcupaLasCoordenadas_lanzaRecursoDuplicado() {
        UbicacionEspecie ubicacion = ubicacion(31L, false);
        when(ubicacionEspecieRepository.findById(31L)).thenReturn(Optional.of(ubicacion));
        when(ubicacionEspecieRepository
                .existsByEspecieIdEspecieAndLatitudAndLongitudAndActivaTrueAndIdUbicacionNot(
                        1L, LATITUD, LONGITUD, 31L))
                .thenReturn(true);

        RecursoDuplicadoException ex = assertThrows(RecursoDuplicadoException.class,
                () -> service.reactivarUbicacion(31L));

        assertTrue(ex.getMessage().contains("coordenadas"),
                "El 409 debe señalar el choque de coordenadas: " + ex.getMessage());
        // El índice parcial de V11 solo admite una fila activa por coordenada:
        // la ubicación tiene que quedarse de baja, sin tocar la base.
        assertEquals(false, ubicacion.getActiva(), "No debe reactivarse nada");
        verify(ubicacionEspecieRepository, never()).saveAndFlush(any());
    }

    @Test
    void reactivarUbicacionDeEspecieDesactivada_lanzaEstadoInvalido() {
        UbicacionEspecie ubicacion = UbicacionEspecie.builder()
                .idUbicacion(33L)
                .especie(especie(false))
                .distrito(distrito())
                .nombreLugar("Cerro Las Pavas")
                .latitud(LATITUD)
                .longitud(LONGITUD)
                .activa(false)
                .build();
        when(ubicacionEspecieRepository.findById(33L)).thenReturn(Optional.of(ubicacion));

        EstadoInvalidoException ex = assertThrows(EstadoInvalidoException.class,
                () -> service.reactivarUbicacion(33L));

        assertTrue(ex.getMessage().contains("especie desactivada"),
                "Debe rechazar con el mismo mensaje que crear y actualizar: " + ex.getMessage());
        assertEquals(false, ubicacion.getActiva(), "No debe reactivarse nada");
        verify(ubicacionEspecieRepository, never()).saveAndFlush(any());
    }

    @Test
    void reactivarUbicacionInexistente_lanzaRecursoNoEncontrado() {
        when(ubicacionEspecieRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.reactivarUbicacion(999L));

        verify(ubicacionEspecieRepository, never()).saveAndFlush(any());
    }

    @Test
    void reactivarUbicacionYaActiva_lanzaEstadoInvalido() {
        UbicacionEspecie ubicacion = ubicacion(32L, true);
        when(ubicacionEspecieRepository.findById(32L)).thenReturn(Optional.of(ubicacion));

        EstadoInvalidoException ex = assertThrows(EstadoInvalidoException.class,
                () -> service.reactivarUbicacion(32L));

        // Simétrico a AN-H13: repetir la operación avisa, no responde 200 en vano.
        assertTrue(ex.getMessage().contains("ya está activa"),
                "Repetir la reactivación debe informar que no cambió nada");
        verify(ubicacionEspecieRepository, never()).saveAndFlush(any());
    }

    // ---------- AN-H06: duplicado de coordenadas ----------

    @Test
    void crearConCoordenadasRepetidas_lanzaRecursoDuplicadoSinConsumirLaSecuencia() {
        when(especieRepository.findById(1L)).thenReturn(Optional.of(especie(true)));
        when(distritoRepository.findById(1L)).thenReturn(Optional.of(distrito()));
        when(ubicacionEspecieRepository
                .existsByEspecieIdEspecieAndLatitudAndLongitudAndActivaTrue(1L, LATITUD, LONGITUD))
                .thenReturn(true);

        RecursoDuplicadoException ex = assertThrows(RecursoDuplicadoException.class,
                () -> service.crear(request()));

        assertTrue(ex.getMessage().contains("coordenadas"),
                "El mensaje ya no debe ser el genérico de integridad: " + ex.getMessage());
        // La clave del hallazgo: se rechaza antes del INSERT, sin gastar un id.
        verify(ubicacionEspecieRepository, never()).save(any());
    }

    @Test
    void actualizarTomandoLasCoordenadasDeOtra_lanzaRecursoDuplicado() {
        when(ubicacionEspecieRepository.findById(11L))
                .thenReturn(Optional.of(ubicacion(11L, true)));
        when(especieRepository.findById(1L)).thenReturn(Optional.of(especie(true)));
        when(distritoRepository.findById(1L)).thenReturn(Optional.of(distrito()));
        when(ubicacionEspecieRepository
                .existsByEspecieIdEspecieAndLatitudAndLongitudAndActivaTrueAndIdUbicacionNot(
                        1L, LATITUD, LONGITUD, 11L))
                .thenReturn(true);

        assertThrows(RecursoDuplicadoException.class,
                () -> service.actualizarUbicacion(11L, request()));

        verify(ubicacionEspecieRepository, never()).save(any());
    }

    // ---------- AN-H05: departamento del distrito ----------

    @Test
    void crear_devuelveElDepartamentoDelDistrito() {
        when(especieRepository.findById(1L)).thenReturn(Optional.of(especie(true)));
        when(distritoRepository.findById(1L)).thenReturn(Optional.of(distrito()));
        when(ubicacionEspecieRepository.save(any())).thenAnswer(inv -> {
            UbicacionEspecie guardada = inv.getArgument(0);
            guardada.setIdUbicacion(9L);
            return guardada;
        });
        when(municipioRepository.findDepartamentosPorMunicipio(anyCollection()))
                .thenReturn(List.of(departamentoDeMunicipio(70L, "San Vicente")));

        UbicacionEspecieResponse response = service.crear(request());

        assertEquals("San Vicente", response.getDepartamentoDistrito(),
                "departamentoDistrito ya no debe llegar en null (AN-H05)");
        assertEquals("San Vicente (distrito)", response.getNombreDistrito());
    }

    @Test
    void listarPorEspecie_resuelveLosDepartamentosEnUnaSolaConsulta() {
        when(especieRepository.existsById(1L)).thenReturn(true);
        when(ubicacionEspecieRepository.findActivasPorEspecieConDistrito(1L))
                .thenReturn(List.of(ubicacion(1L, true), ubicacion(2L, true)));
        when(municipioRepository.findDepartamentosPorMunicipio(anyCollection()))
                .thenReturn(List.of(departamentoDeMunicipio(70L, "San Vicente")));

        List<UbicacionEspecieResponse> respuestas = service.obtenerUbicacionesPorEspecie(1L);

        assertEquals(2, respuestas.size());
        assertTrue(respuestas.stream()
                        .allMatch(r -> "San Vicente".equals(r.getDepartamentoDistrito())),
                "Las dos ubicaciones deben traer su departamento");
        // Dos ubicaciones del mismo municipio: una sola consulta, no una por fila.
        verify(municipioRepository).findDepartamentosPorMunicipio(anyCollection());
    }

    // ---------- datos de apoyo ----------

    private static Especie especie(boolean activa) {
        return Especie.builder()
                .idEspecie(1L)
                .nombreCientifico("Crotalaria longirostrata")
                .activa(activa)
                .build();
    }

    private static Distrito distrito() {
        return Distrito.builder()
                .idDistrito(1L)
                .idMunicipio(70L)
                .nombre("San Vicente (distrito)")
                .activo(true)
                .build();
    }

    private static UbicacionEspecie ubicacion(Long id, boolean activa) {
        return UbicacionEspecie.builder()
                .idUbicacion(id)
                .especie(especie(true))
                .distrito(distrito())
                .nombreLugar("Cerro Las Pavas")
                .latitud(LATITUD)
                .longitud(LONGITUD)
                .activa(activa)
                .build();
    }

    private static UbicacionEspecieRequest request() {
        return UbicacionEspecieRequest.builder()
                .idEspecie(1L)
                .idDistrito(1L)
                .nombreLugar("Cerro Las Pavas")
                .latitud(LATITUD)
                .longitud(LONGITUD)
                .build();
    }

    private static DepartamentoDeMunicipio departamentoDeMunicipio(Long idMunicipio, String nombre) {
        return new DepartamentoDeMunicipio() {
            @Override
            public Long getIdMunicipio() {
                return idMunicipio;
            }

            @Override
            public String getNombreDepartamento() {
                return nombre;
            }
        };
    }
}
