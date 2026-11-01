package sv.edu.ues.fmp.flora.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import sv.edu.ues.fmp.flora.dto.response.UsuarioResponse;
import sv.edu.ues.fmp.flora.service.EmailService;
import sv.edu.ues.fmp.flora.service.JwtService;

/**
 * Pruebas de la autorizacion por permiso: que cada endpoint sensible exija el
 * codigo que le toca, que un permiso no sirva para otro modulo, y que nada de
 * esto haya cerrado los GET publicos del catalogo.
 *
 * <h2>Por que los ids son 999999</h2>
 * Todas las peticiones apuntan a ids que no existen en ninguna base. Es
 * deliberado: lo que se mide es el guard, no los datos. Sin el permiso la
 * respuesta tiene que ser 403 --cortada antes de tocar la base-- y con el
 * permiso 404, que es la prueba de que el guard dejo pasar y la peticion llego
 * hasta la logica de negocio. Un id real haria la prueba dependiente de lo que
 * cada quien tenga sembrado.
 *
 * <h2>Por que los cuerpos son validos</h2>
 * Spring MVC resuelve los argumentos del metodo ANTES de que corra
 * method-security: un {@code @Valid @RequestBody} que no cumple, o un
 * {@code @RequestParam} obligatorio que falta, devuelve 400 sin llegar nunca al
 * {@code @PreAuthorize}. Por eso los cuerpos cumplen Bean Validation y los
 * parametros obligatorios van siempre puestos; si no, estas pruebas darian 400
 * en los dos sentidos y no probarian nada.
 *
 * <h2>Los tokens llevan permisos, no roles</h2>
 * El rol del token es siempre CONSULTOR y lo que cambia de una prueba a otra es
 * la lista de permisos. Es a proposito: la autorizacion se diseño sobre
 * {@code hasAuthority(permiso)} y no sobre {@code hasRole}, asi que el nombre
 * del rol no deberia influir en ninguna respuesta. Si alguien cambiara un
 * {@code @PreAuthorize} a {@code hasRole}, estas pruebas se caerian.
 */
@SpringBootTest
@AutoConfigureMockMvc
// Ninguna prueba deberia escribir --todas fallan en el 404 antes de guardar--
// pero el rollback de @Transactional es la garantia de que un cambio futuro en
// el orden de validaciones de un servicio no deje basura en la base de quien
// corra las pruebas.
@Transactional
class AutorizacionPorPermisoTest {

    private static final String USUARIO_GESTIONAR = "USUARIO_GESTIONAR";
    private static final String ESPECIE_CREAR = "ESPECIE_CREAR";
    private static final String ESPECIE_EDITAR = "ESPECIE_EDITAR";
    private static final String ESPECIE_VALIDAR = "ESPECIE_VALIDAR";
    private static final String ESPECIE_PUBLICAR = "ESPECIE_PUBLICAR";
    private static final String ESPECIE_CONSULTAR = "ESPECIE_CONSULTAR";
    private static final String CATALOGO_GESTIONAR = "CATALOGO_GESTIONAR";
    private static final String MULTIMEDIA_GESTIONAR = "MULTIMEDIA_GESTIONAR";
    private static final String UBICACION_GESTIONAR = "UBICACION_GESTIONAR";

    private static final String ID_INEXISTENTE = "999999";

    /** Cumple Bean Validation; el creador 999999 es lo que hace fallar al servicio. */
    private static final String CUERPO_ESPECIE = """
            {
              "nombreCientifico": "Pruebus autorizacionis",
              "descripcion": "Ficha de prueba de autorizacion",
              "creadaPor": 999999,
              "taxonomia": { "genero": "Pruebus", "especieTaxonomica": "autorizacionis" }
            }
            """;

    private static final String CUERPO_USUARIO_ROL = """
            { "idUsuario": 999999, "idRol": 999999 }
            """;

    private static final String CUERPO_ROL_PERMISO = """
            { "idRol": 999999, "idPermiso": 999999 }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    /** Mismo motivo que en {@link AutenticacionJwtTest}: el contexto completo lo arrastra. */
    @MockitoBean
    private EmailService emailService;

    /**
     * Token de un usuario con rol CONSULTOR y exactamente los permisos que se
     * le pasen.
     */
    private String tokenCon(String... permisos) {
        UsuarioResponse usuario = UsuarioResponse.builder()
                .id(999L)
                .nombres("Prueba")
                .apellidos("Autorizacion")
                .correo("prueba.autorizacion@local")
                .nombreUsuario("prueba.autorizacion")
                .activo(true)
                .build();

        return jwtService.emitir(usuario, List.of("CONSULTOR"), List.of(permisos)).token();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    /** El CONSULTOR del seed V9: autenticado, pero sin ningun permiso de gestion. */
    private String tokenDeConsultor() {
        return tokenCon(ESPECIE_CONSULTAR);
    }

    // ---------- Prioridad 1: la escalacion vertical ----------

    @Test
    void asignarRol_sinUsuarioGestionar_devuelve403ConElFormatoDeErrorDelProyecto() throws Exception {
        mockMvc.perform(post("/api/usuario-roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeConsultor()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_USUARIO_ROL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.estado").value(403))
                .andExpect(jsonPath("$.error").value("Prohibido"))
                .andExpect(jsonPath("$.ruta").value("/api/usuario-roles"))
                .andExpect(jsonPath("$.mensaje").exists())
                // Ni el permiso que faltaba ni trazas internas: el 403 no es un
                // mapa de la autorizacion para quien este tanteando.
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    /**
     * El 404 es la prueba de que el guard dejo pasar: la peticion llego al
     * servicio, que no encontro al usuario 999999.
     */
    @Test
    void asignarRol_conUsuarioGestionar_atraviesaElGuard() throws Exception {
        mockMvc.perform(post("/api/usuario-roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenCon(USUARIO_GESTIONAR)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_USUARIO_ROL))
                .andExpect(status().isNotFound());
    }

    @Test
    void asignarPermisoARol_sinUsuarioGestionar_devuelve403() throws Exception {
        mockMvc.perform(post("/api/rol-permisos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeConsultor()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_ROL_PERMISO))
                .andExpect(status().isForbidden());
    }

    @Test
    void asignarPermisoARol_conUsuarioGestionar_atraviesaElGuard() throws Exception {
        mockMvc.perform(post("/api/rol-permisos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenCon(USUARIO_GESTIONAR)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_ROL_PERMISO))
                .andExpect(status().isNotFound());
    }

    @Test
    void quitarRolAUnUsuario_sinUsuarioGestionar_devuelve403() throws Exception {
        mockMvc.perform(delete("/api/usuario-roles/usuario/1/rol/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeConsultor())))
                .andExpect(status().isForbidden());
    }

    /**
     * El permiso a nivel de clase cubre tambien los GET del modulo de
     * seguridad: saber que roles tiene quien, o que permisos trae un rol, es el
     * mapa de la autorizacion.
     */
    @Test
    void consultarRolesYPermisosDeUnUsuario_sinUsuarioGestionar_devuelve403() throws Exception {
        mockMvc.perform(get("/api/usuario-roles/usuario/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeConsultor())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/usuario-roles/usuario/1/permisos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeConsultor())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/rol-permisos/rol/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeConsultor())))
                .andExpect(status().isForbidden());
    }

    @Test
    void catalogoDeRolesYPermisos_sinUsuarioGestionar_devuelve403() throws Exception {
        mockMvc.perform(get("/api/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeConsultor())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/permisos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeConsultor())))
                .andExpect(status().isForbidden());
    }

    @Test
    void gestionDeUsuarios_sinUsuarioGestionar_devuelve403() throws Exception {
        mockMvc.perform(get("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeConsultor())))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/usuarios/" + ID_INEXISTENTE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeConsultor())))
                .andExpect(status().isForbidden());
    }

    @Test
    void gestionDeUsuarios_conUsuarioGestionar_atraviesaElGuard() throws Exception {
        mockMvc.perform(get("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenCon(USUARIO_GESTIONAR))))
                .andExpect(status().isOk());
    }

    // ---------- Prioridad 2: el flujo editorial de especie ----------

    @Test
    void crearEspecie_sinEspecieCrear_devuelve403() throws Exception {
        mockMvc.perform(post("/api/especies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeConsultor()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_ESPECIE))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearEspecie_conEspecieCrear_atraviesaElGuard() throws Exception {
        mockMvc.perform(post("/api/especies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenCon(ESPECIE_CREAR)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_ESPECIE))
                .andExpect(status().isNotFound());
    }

    @Test
    void validarEspecie_sinEspecieValidar_devuelve403() throws Exception {
        mockMvc.perform(patch("/api/especies/" + ID_INEXISTENTE + "/validar")
                        .param("idValidador", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeConsultor())))
                .andExpect(status().isForbidden());
    }

    @Test
    void validarEspecie_conEspecieValidar_atraviesaElGuard() throws Exception {
        mockMvc.perform(patch("/api/especies/" + ID_INEXISTENTE + "/validar")
                        .param("idValidador", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenCon(ESPECIE_VALIDAR))))
                .andExpect(status().isNotFound());
    }

    /**
     * Los permisos del flujo editorial son finos a proposito: poder editar una
     * ficha no es poder validarla. Si alguien colapsara los cuatro permisos de
     * especie en uno solo, esta prueba lo caza.
     */
    @Test
    void validarEspecie_conEspecieEditarNoAlcanza_devuelve403() throws Exception {
        mockMvc.perform(patch("/api/especies/" + ID_INEXISTENTE + "/validar")
                        .param("idValidador", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenCon(ESPECIE_EDITAR))))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicarEspecie_sinEspeciePublicar_devuelve403() throws Exception {
        mockMvc.perform(patch("/api/especies/" + ID_INEXISTENTE + "/publicar")
                        .param("idPublicador", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeConsultor())))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicarEspecie_conEspeciePublicar_atraviesaElGuard() throws Exception {
        mockMvc.perform(patch("/api/especies/" + ID_INEXISTENTE + "/publicar")
                        .param("idPublicador", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenCon(ESPECIE_PUBLICAR))))
                .andExpect(status().isNotFound());
    }

    /**
     * La separacion que pide el flujo editorial: quien solo valida no publica.
     */
    @Test
    void publicarEspecie_conEspecieValidarNoAlcanza_devuelve403() throws Exception {
        mockMvc.perform(patch("/api/especies/" + ID_INEXISTENTE + "/publicar")
                        .param("idPublicador", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenCon(ESPECIE_VALIDAR))))
                .andExpect(status().isForbidden());
    }

    @Test
    void editarEspecie_sinEspecieEditar_devuelve403() throws Exception {
        mockMvc.perform(patch("/api/especies/" + ID_INEXISTENTE + "/activar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeConsultor())))
                .andExpect(status().isForbidden());
    }

    @Test
    void editarEspecie_conEspecieEditar_atraviesaElGuard() throws Exception {
        mockMvc.perform(patch("/api/especies/" + ID_INEXISTENTE + "/activar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenCon(ESPECIE_EDITAR))))
                .andExpect(status().isNotFound());
    }

    // ---------- Prioridad 3: escritura en el resto del catalogo ----------

    @Test
    void escrituraEnCatalogoAuxiliar_sinCatalogoGestionar_devuelve403() throws Exception {
        mockMvc.perform(patch("/api/habitats/" + ID_INEXISTENTE + "/activar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeConsultor())))
                .andExpect(status().isForbidden());
    }

    @Test
    void escrituraEnCatalogoAuxiliar_conCatalogoGestionar_atraviesaElGuard() throws Exception {
        mockMvc.perform(patch("/api/habitats/" + ID_INEXISTENTE + "/activar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenCon(CATALOGO_GESTIONAR))))
                .andExpect(status().isNotFound());
    }

    @Test
    void escrituraEnMultimedia_sinMultimediaGestionar_devuelve403() throws Exception {
        mockMvc.perform(patch("/api/imagenes-especie/" + ID_INEXISTENTE + "/activar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeConsultor())))
                .andExpect(status().isForbidden());
    }

    @Test
    void escrituraEnMultimedia_conMultimediaGestionar_atraviesaElGuard() throws Exception {
        mockMvc.perform(patch("/api/imagenes-especie/" + ID_INEXISTENTE + "/activar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenCon(MULTIMEDIA_GESTIONAR))))
                .andExpect(status().isNotFound());
    }

    @Test
    void escrituraEnUbicaciones_sinUbicacionGestionar_devuelve403() throws Exception {
        mockMvc.perform(delete("/api/ubicaciones/" + ID_INEXISTENTE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeConsultor())))
                .andExpect(status().isForbidden());
    }

    @Test
    void escrituraEnUbicaciones_conUbicacionGestionar_atraviesaElGuard() throws Exception {
        mockMvc.perform(delete("/api/ubicaciones/" + ID_INEXISTENTE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenCon(UBICACION_GESTIONAR))))
                .andExpect(status().isNotFound());
    }

    /**
     * Un permiso no sirve para otro modulo. Con MULTIMEDIA_GESTIONAR se pueden
     * tocar imagenes y videos, no los catalogos auxiliares, y al reves.
     */
    @Test
    void unPermisoDeOtroModuloNoAbreLaPuerta() throws Exception {
        mockMvc.perform(patch("/api/habitats/" + ID_INEXISTENTE + "/activar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenCon(MULTIMEDIA_GESTIONAR))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/imagenes-especie/" + ID_INEXISTENTE + "/activar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenCon(CATALOGO_GESTIONAR))))
                .andExpect(status().isForbidden());
    }

    // ---------- Lo publico no se toco ----------

    @Test
    void catalogoPublico_sigueAbiertoSinToken() throws Exception {
        mockMvc.perform(get("/api/especies/publicadas")).andExpect(status().isOk());
        mockMvc.perform(get("/api/habitats")).andExpect(status().isOk());
        mockMvc.perform(get("/api/nutrientes")).andExpect(status().isOk());
        mockMvc.perform(get("/api/partes-planta")).andExpect(status().isOk());
        mockMvc.perform(get("/api/beneficios")).andExpect(status().isOk());
    }

    /**
     * El anonimo que intenta escribir sigue recibiendo 401 y no 403: la matriz
     * de rutas lo corta en el filtro, antes de que method-security llegue a
     * evaluar el permiso. Es la distincion que pide el javadoc de
     * {@code GlobalExceptionHandler.manejarAccesoDenegado}: 401 invita a
     * autenticarse, 403 dice que ya se sabe quien eres y no te alcanza.
     */
    @Test
    void escrituraSinToken_sigueDando401YNo403() throws Exception {
        mockMvc.perform(patch("/api/habitats/" + ID_INEXISTENTE + "/activar"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/usuario-roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_USUARIO_ROL))
                .andExpect(status().isUnauthorized());
    }

    /**
     * El login no quedo detras de ningun permiso: exigir uno para conseguir el
     * token seria circular.
     */
    @Test
    void loginSigueSiendoPublico() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"no.existe\",\"clave\":\"no-importa\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Usuario o clave incorrectos"));
    }
}
