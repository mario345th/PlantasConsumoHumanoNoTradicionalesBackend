package sv.edu.ues.fmp.flora.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sv.edu.ues.fmp.flora.dto.request.UsuarioRolRequest;
import sv.edu.ues.fmp.flora.dto.response.PermisoResponse;
import sv.edu.ues.fmp.flora.dto.response.RolResponse;
import sv.edu.ues.fmp.flora.dto.response.UsuarioRolResponse;
import sv.edu.ues.fmp.flora.service.UsuarioRolService;

@Tag(name = "Usuario-Rol", description = "Asignación de roles a usuarios (tabla de unión "
        + "usuario_rol) y consulta de permisos efectivos")
@RestController
@RequestMapping("/api/usuario-roles")
@RequiredArgsConstructor
/*
 * Cierre de la escalacion vertical de privilegios.
 *
 * Hasta aqui estos endpoints solo exigian estar autenticado: cualquier
 * usuario con un token valido, incluso uno de rol CONSULTOR, podia asignarse
 * ADMINISTRADOR o conceder cualquier permiso a cualquier rol. Un login
 * bastaba para volverse administrador.
 *
 * El permiso va a nivel de clase y no por metodo porque aqui no hay ninguna
 * excepcion que atender: ninguno de estos endpoints es publico --la matriz de
 * SecurityConfig ya los deja todos detras de authenticated()-- y ninguno
 * necesita un permiso distinto, los GET incluidos: saber que roles tiene
 * quien, o que permisos trae un rol, es el mapa de la autorizacion y no tiene
 * por que leerlo cualquiera. Anotar la clase ademas evita que un endpoint
 * nuevo quede abierto por olvido, que es exactamente como se abrio esto.
 */
@PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
public class UsuarioRolController {

    private final UsuarioRolService usuarioRolService;

    @Operation(summary = "Listar los roles de un usuario",
            description = "Devuelve los roles asignados al usuario indicado a través de "
                    + "usuario_rol. Un usuario sin roles asignados devuelve una lista vacía, "
                    + "no un error. Por omisión la lista es el histórico completo: incluye "
                    + "los roles desactivados, porque la fila de usuario_rol sobrevive a la "
                    + "baja lógica del rol y sirve para auditar qué se le asignó alguna vez. "
                    + "Con soloActivos=true devuelve únicamente los roles vigentes. Para "
                    + "saber qué puede hacer el usuario de verdad, use "
                    + "/usuario/{idUsuario}/permisos, que ya descarta los roles de baja.")
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    @ApiResponse(responseCode = "400", description = "El id del usuario no es un valor positivo")
    @ApiResponse(responseCode = "404", description = "No existe un usuario con ese id")
    @GetMapping("/usuario/{idUsuario}")
    public ResponseEntity<List<RolResponse>> obtenerRolesDeUsuario(
            @PathVariable Long idUsuario,
            @Parameter(description = "Si es true, excluye los roles desactivados")
            @RequestParam(name = "soloActivos", defaultValue = "false") boolean soloActivos) {

        return ResponseEntity.ok(usuarioRolService.obtenerRolesDeUsuario(idUsuario, soloActivos));
    }

    @Operation(summary = "Listar los permisos efectivos de un usuario",
            description = "Recorre usuario -> usuario_rol -> rol -> rol_permiso -> permiso y "
                    + "devuelve la lista de permisos ya aplanada y sin duplicados, aunque el "
                    + "usuario tenga varios roles que compartan un mismo permiso. Es el dato "
                    + "pensado para construir las autoridades de un usuario autenticado. "
                    + "Solo incluye lo efectivamente otorgado: se descartan los roles "
                    + "desactivados y los permisos desactivados, porque dar de baja un rol "
                    + "es la forma de revocar lo que concedía. Por eso esta lista puede ser "
                    + "más corta que la suma de los permisos de los roles que devuelve "
                    + "/usuario/{idUsuario}, que incluye el histórico.")
    @ApiResponse(responseCode = "200", description = "Listado obtenido")
    @ApiResponse(responseCode = "400", description = "El id del usuario no es un valor positivo")
    @ApiResponse(responseCode = "404", description = "No existe un usuario con ese id")
    @GetMapping("/usuario/{idUsuario}/permisos")
    public ResponseEntity<List<PermisoResponse>> obtenerPermisosEfectivosDeUsuario(
            @PathVariable Long idUsuario) {
        return ResponseEntity.ok(usuarioRolService.obtenerPermisosEfectivosDeUsuario(idUsuario));
    }

    @Operation(summary = "Asignar un rol a un usuario",
            description = "Crea la fila en usuario_rol. La combinación idUsuario + idRol no "
                    + "puede repetirse.")
    @ApiResponse(responseCode = "201", description = "Rol asignado")
    @ApiResponse(responseCode = "400", description = "El cuerpo de la petición no es válido")
    @ApiResponse(responseCode = "404", description = "No existe el usuario o el rol indicado")
    @ApiResponse(responseCode = "409", description = "El usuario ya tiene asignado ese rol, o el usuario/rol está desactivado")
    @PostMapping
    public ResponseEntity<UsuarioRolResponse> asignar(@Valid @RequestBody UsuarioRolRequest request) {
        UsuarioRolResponse creado = usuarioRolService.asignar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @Operation(summary = "Quitar un rol de un usuario",
            description = "Borra físicamente la fila de usuario_rol (la relación en sí no "
                    + "tiene baja lógica: o el usuario tiene el rol, o no lo tiene).")
    @ApiResponse(responseCode = "204", description = "Rol quitado del usuario")
    @ApiResponse(responseCode = "400", description = "Algún id no es un valor positivo")
    @ApiResponse(responseCode = "404", description = "El usuario no tenía asignado ese rol")
    @DeleteMapping("/usuario/{idUsuario}/rol/{idRol}")
    public ResponseEntity<Void> quitar(@PathVariable Long idUsuario, @PathVariable Long idRol) {
        usuarioRolService.quitar(idUsuario, idRol);
        return ResponseEntity.noContent().build();
    }
}
