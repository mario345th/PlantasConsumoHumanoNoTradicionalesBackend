# Módulo de seguridad — Usuario, Rol, Permiso, Usuario-Rol

Documentación de los controladores REST que implementan el modelo de control de
acceso descrito en la guía `GUIA_EXPLICATIVA_TUPLAS_BD_PLANTAS` (Módulo A.
Seguridad y publicación). Cubre `UsuarioController`, `RolController`,
`PermisoController` y `UsuarioRolController`.

Todos los endpoints de este módulo:
- Devuelven JSON.
- Usan el mismo formato de error uniforme (`ErrorResponse`) a través de
  `GlobalExceptionHandler`.
- Validan el cuerpo de la petición con Bean Validation (`@Valid`); una
  violación responde `400 BAD REQUEST` con el detalle campo por campo en
  `erroresValidacion`.

---

## 1. `UsuarioController` — `/api/usuarios`

Administra las cuentas del personal interno (investigadores y
administradores) y el login. No expone contraseñas: `clave_hash` nunca sale
en una respuesta.

| Método | Ruta | Body | Respuesta | Éxito | Errores |
|---|---|---|---|---|---|
| GET | `/api/usuarios?soloActivos={bool}` | — | `List<UsuarioResponse>` | 200 | — |
| GET | `/api/usuarios/{id}` | — | `UsuarioResponse` | 200 | 404 si no existe |
| POST | `/api/usuarios` | `UsuarioCreationRequest` | `UsuarioResponse` | 201 | 400 validación, 409 correo o usuario duplicado |
| PUT | `/api/usuarios/{id}` | `UsuarioUpdateRequest` | `UsuarioResponse` | 200 | 404, 400, 409 |
| DELETE | `/api/usuarios/{id}` | — | — | 204 | 404 |
| POST | `/api/usuarios/login` | `UsuarioLoginRequest` | `UsuarioResponse` | 200 | 401 credenciales inválidas |

### `UsuarioCreationRequest`
```json
{
  "nombre": "Carlos",
  "apellido": "Pérez",
  "correo": "carlos@proyecto.edu.sv",
  "nombreUsuario": "cperez",
  "clave": "contraseñaSegura123"
}
```
Validaciones: todos los campos obligatorios; `correo` con formato válido
(máx. 150); `nombreUsuario` entre 4 y 50 caracteres y solo
`[a-zA-Z0-9._-]`; `clave` entre 8 y 72 caracteres (72 es el límite físico de
BCrypt). La clave nunca se persiste en texto plano: `UsuarioServiceImpl` la
recibe en el DTO y `UsuarioMapper`/`PasswordEncoder` la convierten a
`clave_hash` antes de guardar.

### `UsuarioUpdateRequest`
Igual que el de creación pero sin `clave` (este endpoint no cambia la
contraseña) — mismas validaciones de `nombre`, `apellido`, `correo` y
`nombreUsuario`.

### `UsuarioLoginRequest` → `POST /api/usuarios/login`
```json
{ "usuario": "cperez", "clave": "contraseñaSegura123" }
```
`usuario` acepta indistintamente el correo o el `nombre_usuario`
(`findByCorreoIgnoreCaseOrNombreUsuarioIgnoreCase`). El flujo:
1. Busca la cuenta y exige que esté `activo = true`; si no existe o está
   inactiva, `401` con el mensaje genérico "Usuario o clave incorrectos"
   (no se distingue el motivo, por seguridad).
2. Compara la clave recibida contra `clave_hash` con `PasswordEncoder`.
3. Si coincide, actualiza `ultimo_acceso` a la hora actual y devuelve el
   usuario.

### Reglas de negocio
- **Duplicados**: `correo` y `nombreUsuario` son únicos; se valida antes de
  `crear` y de `actualizar` (excluyendo el propio registro al actualizar).
- **Baja lógica**: `DELETE` no borra la fila, pone `activo = false`
  (`desactivar`). El histórico de especies que el usuario creó/validó/publicó
  no se pierde porque esas columnas son FK a `usuario`, no dependen de que
  la cuenta siga activa.
- **`UsuarioResponse`** incluye `fechaRegistro`, `fechaActualizacion` y
  `ultimoAcceso` con formato `dd/MM/yyyy HH:mm:ss`.

---

## 2. `RolController` — `/api/roles`

CRUD del catálogo de roles internos (`ADMINISTRADOR`, `INVESTIGADOR`, …).

| Método | Ruta | Body | Respuesta | Éxito | Errores |
|---|---|---|---|---|---|
| GET | `/api/roles?soloActivos={bool}` | — | `List<RolResponse>` | 200 | — |
| GET | `/api/roles/{id}` | — | `RolResponse` | 200 | 404 |
| POST | `/api/roles` | `RolRequest` | `RolResponse` | 201 | 400, 409 nombre duplicado |
| PUT | `/api/roles/{id}` | `RolRequest` | `RolResponse` | 200 | 404, 400, 409 |
| DELETE | `/api/roles/{id}` | — | — | 204 | 404 |

### `RolRequest`
```json
{ "nombre": "INVESTIGADOR", "descripcion": "Registra y valida el contenido técnico" }
```
`nombre` obligatorio (máx. 50) y **único** en la base
(`existsByNombreIgnoreCase` al crear, `findByNombreIgnoreCase` excluyendo el
propio id al actualizar). `descripcion` opcional (máx. 250).

### Reglas de negocio
- `DELETE` es baja lógica (`activo = false`), igual que en `usuario`. Un rol
  desactivado deja de listarse en `soloActivos=true`, pero las filas de
  `usuario_rol` y `rol_permiso` que lo referencian no se tocan: si se
  reactiva manualmente, las asignaciones anteriores siguen vigentes.
- No hay endpoint aquí para asociar permisos a un rol (`rol_permiso`); ese
  cruce no tiene controlador propio todavía (ver sección 5, "Pendiente").

---

## 3. `PermisoController` — `/api/permisos` (nuevo)

CRUD del catálogo de permisos. Implementado en este cambio siguiendo
exactamente el mismo patrón que `RolController` (mismo estilo de
capas: DTO → Mapper → Repository → Service → Controller), con la diferencia
de que la unicidad de negocio recae en `codigo`, no en `nombre` (en la tabla
`permiso` solo `codigo` tiene restricción `UNIQUE`; `nombre` admite
repetidos).

| Método | Ruta | Body | Respuesta | Éxito | Errores |
|---|---|---|---|---|---|
| GET | `/api/permisos?soloActivos={bool}` | — | `List<PermisoResponse>` | 200 | — |
| GET | `/api/permisos/{id}` | — | `PermisoResponse` | 200 | 404 |
| POST | `/api/permisos` | `PermisoRequest` | `PermisoResponse` | 201 | 400, 409 código duplicado |
| PUT | `/api/permisos/{id}` | `PermisoRequest` | `PermisoResponse` | 200 | 404, 400, 409 |
| DELETE | `/api/permisos/{id}` | — | — | 204 | 404 |

### `PermisoRequest`
```json
{
  "codigo": "ESPECIE_VALIDAR",
  "nombre": "Validar especies",
  "descripcion": "Autoriza revisión científica"
}
```
- `codigo`: obligatorio, máx. 80, y restringido a `^[A-Z0-9_]+$` (mayúsculas,
  dígitos y guion bajo) — es el valor que el backend compararía contra el
  permiso requerido por cada endpoint protegido, así que se fuerza el mismo
  formato que ya usan los ejemplos del dominio (`ESPECIE_VALIDAR`,
  `USUARIO_GESTIONAR`, `ESPECIE_CONSULTAR`).
- `nombre`: obligatorio, máx. 120, texto libre para mostrar en pantallas de
  administración.
- `descripcion`: opcional, máx. 250.

### `PermisoResponse`
```json
{
  "id": 4,
  "codigo": "ESPECIE_VALIDAR",
  "nombre": "Validar especies",
  "descripcion": "Autoriza revisión científica",
  "activo": true
}
```

### Reglas de negocio
- **Duplicados por `codigo`**: `crear` rechaza con `409` si ya existe un
  permiso con ese código (`existsByCodigoIgnoreCase`); `actualizar` hace la
  misma comprobación excluyendo el propio id
  (`findByCodigoIgnoreCase(...).filter(id distinto)`), igual que el patrón de
  `RolServiceImpl`.
- **Baja lógica**: `DELETE /api/permisos/{id}` no borra la fila, marca
  `activo = false`. Las filas de `rol_permiso` que ya apuntan a ese permiso
  no se eliminan: si un permiso se desactiva, los roles que lo tenían
  asignado deberían dejar de poder ejercerlo en tiempo de ejecución (eso lo
  decide quien valide permisos en cada endpoint protegido, filtrando también
  por `permiso.activo`, no solo por la existencia de la fila en
  `rol_permiso`).
- Archivos nuevos, mismo paquete que sus equivalentes de `rol`:
  `dto/request/PermisoRequest.java`, `dto/response/PermisoResponse.java`,
  `mapper/PermisoMapper.java`, `repository/PermisoRepository.java`,
  `service/PermisoService.java`, `service/impl/PermisoServiceImpl.java`,
  `controller/PermisoController.java`. No requirió migración Flyway nueva:
  la tabla `permiso` ya existe en el esquema base (`baseline-version=3`) y
  la entidad `Permiso` ya estaba mapeada.

---

## 4. `UsuarioRolController` — `/api/usuario-roles`

Consulta la relación muchos-a-muchos `usuario_rol` (qué roles tiene cada
usuario). Es de solo lectura: **no expone todavía** los endpoints para
asignar o quitar un rol.

| Método | Ruta | Body | Respuesta | Éxito | Errores |
|---|---|---|---|---|---|
| GET | `/api/usuario-roles/usuario/{idUsuario}` | — | `List<RolResponse>` | 200 | 400 id inválido, 404 usuario inexistente |

`UsuarioRolServiceImpl.obtenerRolesDeUsuario`:
1. Valida que `idUsuario` sea positivo (`IdInvalidoException` → 400).
2. Verifica que el usuario exista (`RecursoNoEncontradoException` → 404).
3. Ejecuta `UsuarioRolRepository.findRolesByUsuarioId` (JPQL sobre
   `UsuarioRol` que proyecta `ur.rol`) y devuelve la lista de roles como
   `RolResponse`, mapeada por `UsuarioRolMapper` (que delega en `RolMapper`).

### Pendiente en este controlador
Ya existen `UsuarioRolRequest` (`idUsuario`, `idRol`, ambos `@NotNull
@Positive`) y la entidad `UsuarioRol` con clave compuesta
(`usuario_rol_id`, `fecha_asignacion` con default `now()`), pero **no hay
endpoint que los use todavía**: falta un `POST /api/usuario-roles` para
asignar un rol a un usuario y un `DELETE
/api/usuario-roles/{idUsuario}/{idRol}` para quitarlo. `UsuarioRolService`
solo declara `obtenerRolesDeUsuario`. Si se requiere completar el CRUD de
esta tabla de unión, sería el siguiente paso natural — puedo implementarlo
siguiendo el mismo patrón (validar existencia de usuario y rol, rechazar con
`409` si la combinación `id_usuario + id_rol` ya existe, ya que no puede
repetirse según la guía).

---

## 5. Resumen de rutas del módulo

```
GET    /api/usuarios?soloActivos=
GET    /api/usuarios/{id}
POST   /api/usuarios
PUT    /api/usuarios/{id}
DELETE /api/usuarios/{id}
POST   /api/usuarios/login

GET    /api/roles?soloActivos=
GET    /api/roles/{id}
POST   /api/roles
PUT    /api/roles/{id}
DELETE /api/roles/{id}

GET    /api/permisos?soloActivos=
GET    /api/permisos/{id}
POST   /api/permisos
PUT    /api/permisos/{id}
DELETE /api/permisos/{id}

GET    /api/usuario-roles/usuario/{idUsuario}
```

Todas visibles en Swagger UI (`/swagger-ui.html`) mientras
`springdoc.swagger-ui.enabled` no esté desactivado por perfil.
