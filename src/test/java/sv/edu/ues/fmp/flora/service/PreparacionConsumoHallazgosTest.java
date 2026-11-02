package sv.edu.ues.fmp.flora.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import sv.edu.ues.fmp.flora.dto.request.PreparacionConsumoRequest;
import sv.edu.ues.fmp.flora.entity.EspecieParteComestible;
import sv.edu.ues.fmp.flora.entity.enums.TipoConsumo;
import sv.edu.ues.fmp.flora.exception.RecursoDuplicadoException;
import sv.edu.ues.fmp.flora.mapper.PreparacionConsumoMapper;
import sv.edu.ues.fmp.flora.repository.EspecieParteComestibleRepository;
import sv.edu.ues.fmp.flora.repository.PreparacionConsumoRepository;
import sv.edu.ues.fmp.flora.service.impl.PreparacionConsumoServiceImpl;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las tres observaciones de Preparacion de Consumo del 3er reporte de QA:
 * coherencia tipoConsumo/requiereCoccion (prueba 037), tope de
 * tiempoPreparacionMin (prueba 039) y duplicado que solo difiere en una tilde
 * (prueba 046).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PreparacionConsumoHallazgosTest {

    private static Validator validator;

    @Mock
    private PreparacionConsumoRepository repository;

    @Mock
    private EspecieParteComestibleRepository parteRepository;

    @Mock
    private PreparacionConsumoMapper mapper;

    @InjectMocks
    private PreparacionConsumoServiceImpl service;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    // ---------- coherencia tipoConsumo / requiereCoccion (prueba 037) ----------

    @Test
    void crudoQueRequiereCoccion_esRechazado() {
        PreparacionConsumoRequest request = valida();
        request.setTipoConsumo(TipoConsumo.CRUDO);
        request.setRequiereCoccion(true);

        Set<ConstraintViolation<PreparacionConsumoRequest>> violaciones = validator.validate(request);

        assertTrue(violaciones.stream()
                        .anyMatch(v -> v.getMessage().contains("crudo no puede requerir cocción")),
                "CRUDO con requiereCoccion=true se contradice y debe dar 400");
    }

    @Test
    void crudoSinCoccion_esAceptado() {
        PreparacionConsumoRequest request = valida();
        request.setTipoConsumo(TipoConsumo.CRUDO);
        request.setRequiereCoccion(false);

        assertTrue(validator.validate(request).isEmpty(), "CRUDO sin cocción es coherente");
    }

    @Test
    void cocidoQueRequiereCoccion_esAceptado() {
        PreparacionConsumoRequest request = valida();
        request.setTipoConsumo(TipoConsumo.COCIDO);
        request.setRequiereCoccion(true);

        assertTrue(validator.validate(request).isEmpty(), "COCIDO con cocción es lo esperado");
    }

    @Test
    void crudoConCoccionOmitida_esAceptado() {
        PreparacionConsumoRequest request = valida();
        request.setTipoConsumo(TipoConsumo.CRUDO);
        request.setRequiereCoccion(null);

        assertTrue(validator.validate(request).isEmpty(),
                "Omitir requiereCoccion equivale a false, así que no contradice a CRUDO");
    }

    // ---------- tope de tiempoPreparacionMin (prueba 039) ----------

    @Test
    void tiempoDePreparacionDesmedido_esRechazado() {
        PreparacionConsumoRequest request = valida();
        request.setTiempoPreparacionMin(999_999_999);

        assertTrue(falla(validator.validate(request), "tiempoPreparacionMin"),
                "999999999 minutos debe rechazarse por el tope de 1440");
    }

    @Test
    void tiempoDePreparacionEnElTope_esAceptado() {
        PreparacionConsumoRequest request = valida();
        request.setTiempoPreparacionMin(1440);

        assertFalse(falla(validator.validate(request), "tiempoPreparacionMin"),
                "El límite de 1440 minutos es inclusivo");
    }

    // ---------- duplicado por tilde (prueba 046) ----------

    @Test
    void nombreQueSoloDifiereEnLaTilde_seConsideraDuplicado() {
        when(parteRepository.findById(1L)).thenReturn(Optional.of(parteActiva()));
        when(repository.findNombresPorParte(1L))
                .thenReturn(List.of("Pupusas de queso con loroco"));

        PreparacionConsumoRequest request = valida();
        request.setNombre("Pupusas de queso con lóroco");

        assertThrows(RecursoDuplicadoException.class, () -> service.crear(request));

        verify(repository, never()).save(any());
    }

    @Test
    void nombreQueSoloDifiereEnMayusculasYEspacios_seConsideraDuplicado() {
        when(parteRepository.findById(1L)).thenReturn(Optional.of(parteActiva()));
        when(repository.findNombresPorParte(1L))
                .thenReturn(List.of("Pupusas de queso con loroco"));

        PreparacionConsumoRequest request = valida();
        request.setNombre("  PUPUSAS   DE QUESO CON LOROCO ");

        assertThrows(RecursoDuplicadoException.class, () -> service.crear(request));
    }

    @Test
    void nombreDistintoDeVerdad_seAcepta() {
        when(parteRepository.findById(1L)).thenReturn(Optional.of(parteActiva()));
        when(repository.findNombresPorParte(1L))
                .thenReturn(List.of("Pupusas de queso con loroco"));
        when(mapper.toEntity(any(), any())).thenReturn(null);

        PreparacionConsumoRequest request = valida();
        request.setNombre("Tamal de chipilín");

        service.crear(request);

        verify(repository).save(any());
    }

    // ---------- datos de apoyo ----------

    private static EspecieParteComestible parteActiva() {
        return EspecieParteComestible.builder()
                .idEspecieParte(1L)
                .activa(true)
                .build();
    }

    private static PreparacionConsumoRequest valida() {
        PreparacionConsumoRequest request = new PreparacionConsumoRequest();
        request.setIdEspecieParte(1L);
        request.setNombre("Pupusas de queso con loroco");
        request.setDescripcion("Pupusas tradicionales rellenas de queso y loroco");
        request.setTipoConsumo(TipoConsumo.ASADO);
        request.setRequiereCoccion(true);
        request.setProcedimiento("1. Picar el loroco. 2. Rellenar la masa. 3. Cocer en comal.");
        request.setTiempoPreparacionMin(45);
        request.setNumeroPorciones(10);
        return request;
    }

    private static boolean falla(Set<ConstraintViolation<PreparacionConsumoRequest>> violaciones,
                                 String campo) {
        return violaciones.stream().anyMatch(v -> campo.equals(v.getPropertyPath().toString()));
    }
}
