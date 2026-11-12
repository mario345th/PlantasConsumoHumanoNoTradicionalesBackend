package sv.edu.ues.fmp.flora.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import sv.edu.ues.fmp.flora.config.SiembraDeFichas;
import sv.edu.ues.fmp.flora.config.SiembraDeFichas.Ficha;
import sv.edu.ues.fmp.flora.dto.response.UsuarioResponse;
import sv.edu.ues.fmp.flora.entity.NombreComun;
import sv.edu.ues.fmp.flora.entity.enums.EstadoPublicacion;
import sv.edu.ues.fmp.flora.repository.NombreComunRepository;

/**
 * Red de regresion de la regla "un nombre comun desactivado no puede ser el
 * principal", por los <strong>dos</strong> caminos que marcan un principal.
 *
 * <h2>Lo que estaba mal</h2>
 * {@code PATCH /api/nombres-comunes/{id}/marcar-principal} comprobaba que el
 * nombre estuviera activo, pero el {@code PUT} no: bastaba mandar
 * {@code esPrincipal = true} sobre un nombre dado de baja para saltarse la
 * regla y dejar a la especie con un nombre de cabecera que no se muestra en
 * ninguna parte. La base tampoco lo impedia:
 * {@code uk_nombre_comun_principal} es un indice unico PARCIAL
 * {@code (id_especie) WHERE es_principal AND activo}, asi que una fila
 * {@code es_principal = true, activo = false} queda simplemente fuera del
 * indice.
 * <p>
 * El arreglo fue extraer la comprobacion a un metodo y llamarlo desde los dos
 * caminos. Esta clase es lo que faltaba: hasta ahora no habia ninguna prueba de
 * nombre comun, de modo que quitar la llamada del PUT --que es como nacio el
 * problema-- no habria roto nada.
 *
 * <h2>Por que tambien se prueba el camino feliz</h2>
 * Un 409 puede venir de cualquier parte. Reactivando el mismo nombre y
 * promoviendolo despues, la prueba demuestra que lo que lo rechazaba era el
 * estado inactivo y no otra regla de paso: {@code activar} y luego
 * {@code marcar-principal} sobre esa misma fila responden 200.
 *
 * <h2>Datos</h2>
 * La ficha y los dos nombres se siembran aqui, dentro de la transaccion de la
 * prueba, y el rollback de {@code @Transactional} se los lleva.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NombreComunInactivoNoPrincipalTest {

    private static final String ESPECIE_EDITAR = "ESPECIE_EDITAR";

    private static final String MENSAJE_ESPERADO =
            "Un nombre común desactivado no puede marcarse como principal.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private NombreComunRepository nombreComunRepository;

    @MockitoBean
    private EmailService emailService;

    /** El principal activo que la ficha trae de fabrica. */
    private NombreComun principal;

    /** El que esta dado de baja y que no puede llegar a principal. */
    private NombreComun inactivo;

    @BeforeEach
    void sembrar() {
        Ficha ficha = new SiembraDeFichas(entityManager).sembrar(EstadoPublicacion.BORRADOR);

        principal = ficha.nombreComun();

        inactivo = NombreComun.builder()
                .especie(ficha.especie())
                .nombre("Nombre dado de baja")
                .region("Prueba")
                .esPrincipal(false)
                .activo(false)
                .build();

        entityManager.persist(inactivo);
        entityManager.flush();
    }

    private String token() {
        UsuarioResponse usuario = UsuarioResponse.builder()
                .id(999L)
                .nombres("Prueba")
                .apellidos("NombreComun")
                .correo("prueba.nombrecomun@local")
                .nombreUsuario("prueba.nombrecomun")
                .activo(true)
                .build();

        return "Bearer " + jwtService
                .emitir(usuario, List.of("CONSULTOR"), List.of(ESPECIE_EDITAR))
                .token();
    }

    /**
     * Cumple Bean Validation: {@code nombre} obligatorio y con al menos una
     * letra, {@code esPrincipal} obligatorio. Si no, el 400 llegaria antes que
     * la regla y la prueba no probaria nada.
     */
    private static String cuerpoConEsPrincipal(boolean esPrincipal) {
        return """
                {
                  "nombre": "Nombre dado de baja",
                  "region": "Prueba",
                  "esPrincipal": %s
                }
                """.formatted(esPrincipal);
    }

    // ---------- Los dos caminos lo rechazan ----------

    @Test
    void put_conEsPrincipalTrueSobreUnNombreInactivo_devuelve409() throws Exception {
        mockMvc.perform(put("/api/nombres-comunes/" + inactivo.getIdNombreComun())
                        .header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoConEsPrincipal(true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.estado").value(409))
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_ESPERADO));

        assertNoCambio();
    }

    @Test
    void patchMarcarPrincipal_sobreUnNombreInactivo_devuelve409() throws Exception {
        mockMvc.perform(patch("/api/nombres-comunes/" + inactivo.getIdNombreComun()
                        + "/marcar-principal")
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.estado").value(409))
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_ESPERADO));

        assertNoCambio();
    }

    /**
     * El PUT que no pide el principal sigue sirviendo para editar un nombre
     * inactivo: la regla rechaza la promocion, no toda modificacion. Si
     * cerrara el PUT entero, editarle la region a un registro de baja seria
     * imposible.
     */
    @Test
    void put_sinPedirElPrincipal_sigueEditandoUnNombreInactivo() throws Exception {
        mockMvc.perform(put("/api/nombres-comunes/" + inactivo.getIdNombreComun())
                        .header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoConEsPrincipal(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esPrincipal").value(false));

        assertNoCambio();
    }

    /**
     * Reactivado, el mismo nombre si puede promoverse: la demostracion de que
     * el 409 anterior venia del estado inactivo y de nada mas.
     */
    @Test
    void reactivado_elMismoNombreSiPuedeSerPrincipal() throws Exception {
        mockMvc.perform(patch("/api/nombres-comunes/" + inactivo.getIdNombreComun() + "/activar")
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/nombres-comunes/" + inactivo.getIdNombreComun()
                        + "/marcar-principal")
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esPrincipal").value(true));

        entityManager.flush();

        assertTrue(recargar(inactivo).getEsPrincipal(), "no quedo como principal");
        // Y la especie no se queda con dos: el anterior cede el puesto.
        assertFalse(recargar(principal).getEsPrincipal(),
                "la especie quedo con dos nombres principales");
    }

    /** Ni el principal cambio de manos, ni el inactivo se promovio. */
    private void assertNoCambio() {
        entityManager.flush();

        assertFalse(recargar(inactivo).getEsPrincipal(),
                "el nombre inactivo quedo marcado como principal");
        assertFalse(recargar(inactivo).getActivo(),
                "el nombre inactivo se reactivo solo");
        assertTrue(recargar(principal).getEsPrincipal(),
                "la especie perdio su nombre principal");
    }

    private NombreComun recargar(NombreComun nombre) {
        return nombreComunRepository.findById(nombre.getIdNombreComun()).orElseThrow();
    }
}
