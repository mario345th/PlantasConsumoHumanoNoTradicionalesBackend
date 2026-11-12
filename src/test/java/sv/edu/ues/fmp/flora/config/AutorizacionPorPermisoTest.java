package sv.edu.ues.fmp.flora.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
 * <p>
 * {@code validar} y {@code publicar} ya no reciben ningun parametro: los
 * {@code idValidador} / {@code idPublicador} que estas pruebas mandaban
 * desaparecieron del controlador cuando el responsable paso a tomarse del
 * token.
 *
 * <h2>Los tokens llevan permisos, no roles</h2>
 * El rol del token es siempre CONSULTOR y lo que cambia de una prueba a otra es
 * la lista de permisos. Es a proposito: la autorizacion se diseño sobre
 * {@code hasAuthority(permiso)} y no sobre {@code hasRole}, asi que el nombre
 * del rol no deberia influir en ninguna respuesta. Si alguien cambiara un
 * {@code @PreAuthorize} a {@code hasRole}, estas pruebas se caerian.
 * <p>
 * Que CONSULTOR ya no exista en la tabla {@code rol} --lo quito {@code V12},
 * que dejo solo ADMINISTRADOR e INVESTIGADOR-- es justamente la demostracion:
 * el claim es texto que nadie contrasta contra la base, y estas pruebas
 * siguieron pasando sin tocar una linea.
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

    /**
     * Cumple Bean Validation. Lo que hace fallar al servicio ya no es un
     * creador inventado en el cuerpo --{@code creadaPor} se quito del DTO,
     * ahora sale del token-- sino que el {@code sub} del token de estas
     * pruebas es 999, un usuario que no existe en ninguna base: el servicio
     * responde 404 "No existe el usuario creador con id 999", que es
     * igualmente la prueba de que el guard dejo pasar.
     */
    private static final String CUERPO_ESPECIE = """
            {
              "nombreCientifico": "Pruebus autorizacionis",
              "descripcion": "Ficha de prueba de autorizacion",
              "taxonomia": { "genero": "Pruebus", "especieTaxonomica": "autorizacionis" }
            }
            """;

    private static final String CUERPO_USUARIO_ROL = """
            { "idUsuario": 999999, "idRol": 999999 }
            """;

    private static final String CUERPO_ROL_PERMISO = """
            { "idRol": 999999, "idPermiso": 999999 }
            """;

    /**
     * Para el POST de video de preparacion (MM-106). Cumple
     * {@code VideoPreparacionRequest} --titulo y urlVideo obligatorios, la URL
     * con dominio y esquema-- por el motivo que explica el encabezado de la
     * clase: un cuerpo invalido se rechaza con 400 antes de llegar al
     * {@code @PreAuthorize} y la prueba no mediria el guard.
     */
    private static final String CUERPO_VIDEO_PREPARACION = """
            {
              "titulo": "Elaboración paso a paso de la receta",
              "urlVideo": "https://www.youtube.com/watch?v=mm106",
              "duracionSegundos": 180
            }
            """;

    /** Mismo criterio para los otros tres cuerpos del modulo de multimedia. */
    private static final String CUERPO_VIDEO_ESPECIE = """
            {
              "titulo": "La planta en su hábitat",
              "urlVideo": "https://www.youtube.com/watch?v=especie",
              "duracionSegundos": 120
            }
            """;

    private static final String CUERPO_IMAGEN_ESPECIE = """
            {
              "tipoImagen": "PLANTA_COMPLETA",
              "urlImagen": "https://ejemplo.com/imagenes/planta.jpg",
              "textoAlternativo": "La planta completa vista de frente"
            }
            """;

    private static final String CUERPO_IMAGEN_PREPARACION = """
            {
              "urlImagen": "https://ejemplo.com/imagenes/receta.jpg",
              "textoAlternativo": "El platillo servido y listo para consumir"
            }
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

    /** Autenticado, pero sin ningun permiso de gestion: solo puede consultar. */
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
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeConsultor())))
                .andExpect(status().isForbidden());
    }

    @Test
    void validarEspecie_conEspecieValidar_atraviesaElGuard() throws Exception {
        mockMvc.perform(patch("/api/especies/" + ID_INEXISTENTE + "/validar")
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
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenCon(ESPECIE_EDITAR))))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicarEspecie_sinEspeciePublicar_devuelve403() throws Exception {
        mockMvc.perform(patch("/api/especies/" + ID_INEXISTENTE + "/publicar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeConsultor())))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicarEspecie_conEspeciePublicar_atraviesaElGuard() throws Exception {
        mockMvc.perform(patch("/api/especies/" + ID_INEXISTENTE + "/publicar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenCon(ESPECIE_PUBLICAR))))
                .andExpect(status().isNotFound());
    }

    /**
     * La separacion que pide el flujo editorial: quien solo valida no publica.
     */
    @Test
    void publicarEspecie_conEspecieValidarNoAlcanza_devuelve403() throws Exception {
        mockMvc.perform(patch("/api/especies/" + ID_INEXISTENTE + "/publicar")
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

    /**
     * MM-106. La ruta plana de video de preparacion nacio sin
     * {@code @PreAuthorize} y la matriz de {@link SecurityConfig} cierra las
     * escrituras solo con {@code anyRequest().authenticated()}: QA creo un
     * video con un token sin un solo rol (201 donde la ruta anidada
     * {@code POST /api/preparaciones-consumo/{id}/videos} responde 403).
     * <p>
     * Estas dos pruebas son el blindaje del arreglo: si alguien quita la
     * anotacion del controlador, la primera pasa a devolver 404 --el guard ya
     * no corta y la peticion llega al servicio-- y el build se cae aqui.
     */
    @Test
    void crearVideoDePreparacionEnRutaPlana_sinMultimediaGestionar_devuelve403() throws Exception {
        mockMvc.perform(post("/api/videos-preparacion/preparacion/" + ID_INEXISTENTE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDeConsultor()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VIDEO_PREPARACION))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearVideoDePreparacionEnRutaPlana_conMultimediaGestionar_atraviesaElGuard() throws Exception {
        mockMvc.perform(post("/api/videos-preparacion/preparacion/" + ID_INEXISTENTE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenCon(MULTIMEDIA_GESTIONAR)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VIDEO_PREPARACION))
                .andExpect(status().isNotFound());
    }

    // ---------- MM-106 extendido: las diecinueve escrituras del modulo ----------

    /**
     * Una escritura del modulo de multimedia: el verbo, la ruta y el cuerpo
     * que hay que mandarle, o {@code null} cuando el verbo no lleva cuerpo.
     * <p>
     * El {@code toString} es lo que JUnit pone como nombre de cada caso, para
     * que el informe diga {@code PUT /api/videos-especie/999999} y no
     * {@code [3] Escritura[verbo=PUT, ...]}: cuando una de estas pruebas se
     * cae, lo primero que hace falta saber es que ruta se quedo sin guard.
     */
    private record Escritura(HttpMethod verbo, String ruta, String cuerpo) {

        @Override
        public String toString() {
            return verbo.name() + " " + ruta;
        }
    }

    /**
     * Las diecinueve rutas de escritura de los ocho controladores de
     * multimedia.
     *
     * <h2>Por que la lista entera y no una ruta de muestra</h2>
     * MM-106 no fue un descuido de una ruta: fue que la matriz de
     * {@link SecurityConfig} cierra las escrituras con
     * {@code anyRequest().authenticated()} --cualquier token sirve-- y el
     * permiso lo pone cada {@code @PreAuthorize} del controlador. Una
     * anotacion que falte no la delata nada: la ruta responde 201 a quien
     * traiga un token sin un solo permiso, que es exactamente lo que QA
     * encontro en {@code POST /api/videos-preparacion/preparacion/{id}}.
     * El codigo esta hoy en 8 de 8 controladores, pero solo dos rutas estaban
     * fijadas por una prueba; las demas se podian perder en cualquier
     * refactor.
     * <p>
     * Con la lista completa, quitar cualquiera de las diecinueve anotaciones
     * cae el build: el caso sin permiso de esa ruta pasa de 403 a 404, porque
     * la peticion ya llega al servicio.
     *
     * <h2>Lo que no entra</h2>
     * Solo los verbos de escritura. Los {@code GET} del modulo siguen siendo
     * publicos a proposito --son el catalogo-- y su visibilidad la prueban
     * {@link EspecieSubRecursosVisibilidadTest} y
     * {@link EspecieRutasPlanasVisibilidadTest}, que es otra cosa: ahi lo que
     * se mide es el estado de la ficha de la que cuelga el contenido, no un
     * permiso.
     */
    private static List<Escritura> escriturasDeMultimedia() {
        return List.of(
                // EspecieImagenController: POST anidado bajo la especie.
                new Escritura(HttpMethod.POST,
                        "/api/especies/" + ID_INEXISTENTE + "/imagenes", CUERPO_IMAGEN_ESPECIE),

                // EspecieVideoController.
                new Escritura(HttpMethod.POST,
                        "/api/especies/" + ID_INEXISTENTE + "/videos", CUERPO_VIDEO_ESPECIE),

                // ImagenEspecieController: la ruta plana de la imagen.
                new Escritura(HttpMethod.PUT,
                        "/api/imagenes-especie/" + ID_INEXISTENTE, CUERPO_IMAGEN_ESPECIE),
                new Escritura(HttpMethod.DELETE,
                        "/api/imagenes-especie/" + ID_INEXISTENTE, null),
                new Escritura(HttpMethod.PATCH,
                        "/api/imagenes-especie/" + ID_INEXISTENTE + "/activar", null),
                new Escritura(HttpMethod.PATCH,
                        "/api/imagenes-especie/" + ID_INEXISTENTE + "/marcar-principal", null),

                // ImagenPreparacionController.
                new Escritura(HttpMethod.PUT,
                        "/api/imagenes-preparacion/" + ID_INEXISTENTE, CUERPO_IMAGEN_PREPARACION),
                new Escritura(HttpMethod.DELETE,
                        "/api/imagenes-preparacion/" + ID_INEXISTENTE, null),
                new Escritura(HttpMethod.PATCH,
                        "/api/imagenes-preparacion/" + ID_INEXISTENTE + "/activar", null),
                new Escritura(HttpMethod.PATCH,
                        "/api/imagenes-preparacion/" + ID_INEXISTENTE + "/marcar-principal", null),

                // PreparacionImagenController.
                new Escritura(HttpMethod.POST,
                        "/api/preparaciones-consumo/" + ID_INEXISTENTE + "/imagenes",
                        CUERPO_IMAGEN_PREPARACION),

                // PreparacionVideoController: la ruta anidada que si cortaba
                // cuando la plana de MM-106 no lo hacia.
                new Escritura(HttpMethod.POST,
                        "/api/preparaciones-consumo/" + ID_INEXISTENTE + "/videos",
                        CUERPO_VIDEO_PREPARACION),

                // VideoEspecieController.
                new Escritura(HttpMethod.PUT,
                        "/api/videos-especie/" + ID_INEXISTENTE, CUERPO_VIDEO_ESPECIE),
                new Escritura(HttpMethod.DELETE,
                        "/api/videos-especie/" + ID_INEXISTENTE, null),
                new Escritura(HttpMethod.PATCH,
                        "/api/videos-especie/" + ID_INEXISTENTE + "/activar", null),

                // VideoPreparacionController, el del hallazgo. El POST va
                // tambien aqui, aunque las dos pruebas de arriba ya lo fijen
                // con su nombre y su explicacion: la lista tiene que ser las
                // diecinueve rutas, no las diecinueve menos la que se recuerda
                // a mano.
                new Escritura(HttpMethod.POST,
                        "/api/videos-preparacion/preparacion/" + ID_INEXISTENTE,
                        CUERPO_VIDEO_PREPARACION),
                new Escritura(HttpMethod.PUT,
                        "/api/videos-preparacion/" + ID_INEXISTENTE, CUERPO_VIDEO_PREPARACION),
                new Escritura(HttpMethod.DELETE,
                        "/api/videos-preparacion/" + ID_INEXISTENTE, null),
                new Escritura(HttpMethod.PATCH,
                        "/api/videos-preparacion/" + ID_INEXISTENTE + "/activar", null));
    }

    /** La peticion de una {@link Escritura} con el token que se le pase. */
    private static MockHttpServletRequestBuilder peticionDe(Escritura escritura, String token) {
        MockHttpServletRequestBuilder peticion = request(escritura.verbo(), escritura.ruta())
                .header(HttpHeaders.AUTHORIZATION, bearer(token));

        return escritura.cuerpo() == null
                ? peticion
                : peticion.contentType(MediaType.APPLICATION_JSON).content(escritura.cuerpo());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("escriturasDeMultimedia")
    void escriturasDeMultimedia_sinMultimediaGestionar_devuelven403(Escritura escritura)
            throws Exception {

        mockMvc.perform(peticionDe(escritura, tokenDeConsultor()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.estado").value(403));
    }

    /**
     * El contrapunto obligado: con el permiso, la misma peticion llega al
     * servicio y recibe el 404 del id inexistente.
     * <p>
     * Sin esta mitad, un {@code @PreAuthorize} con el permiso equivocado --o
     * una ruta cerrada de mas-- daria 403 siempre y la prueba de arriba
     * pasaria igual.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("escriturasDeMultimedia")
    void escriturasDeMultimedia_conMultimediaGestionar_atraviesanElGuard(Escritura escritura)
            throws Exception {

        mockMvc.perform(peticionDe(escritura, tokenCon(MULTIMEDIA_GESTIONAR)))
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
