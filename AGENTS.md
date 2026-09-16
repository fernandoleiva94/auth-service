# Auth Service

## Propósito

Servicio de autenticación de la plataforma. Emite los JWT que consumen todos
los demás servicios.

Responsabilidades:
- login por usuario y contraseña
- emisión del JWT con los claims de identidad, tenant y permisos

Un solo endpoint: `POST /v1/auth/login`.

## Stack

- Java 17
- Spring Boot 3.1.4
- Spring Data JPA
- PostgreSQL (driver 42.7.3)
- Spring Security
- jjwt 0.11.5
- Maven (wrapper incluido)

Artefacto: `com.sevenb.auth:auth-service`.
Paquete base: `com.sevenb.auth.auth_service` (con guion bajo, porque
`auth-service` no es un identificador Java válido).
Puerto: 8081. Sin context-path.

No hay Lombok, Flyway, Testcontainers ni OpenAPI.

Este servicio está atrasado respecto del resto de la plataforma
(centauro-pos-manager usa Boot 3.5.4 y jjwt 0.12.x). Actualizarlo es un
cambio propio, no algo para hacer de paso.

## El JWT es el contrato más importante de este repo

Firma HS256. Expiración: 5 días.

El secreto está **hardcodeado** en `service/JwtService.java`:
`SECRET_KEY = "my-secret-key-which-should-be-very-long-and-secure"`.
Los servicios que validan el token lo leen de la variable `JWT_SECRET` y
deben tener exactamente ese valor. Cambiarlo invalida todas las sesiones y
rompe pos-manager y purchase-manager al mismo tiempo.

Claims emitidos hoy:

| Claim | Contenido |
|---|---|
| `sub` | username |
| `id` | `Person.id` (no `User.id`) |
| `user` | username (duplica `sub`) |
| `name` | nombre para mostrar: nombre + apellido, o username si faltan |
| `ownerId` | `Person.id` (mismo valor que `id` en el flujo actual) |
| `tenantId` | `Person.tenantId` |
| `taxCondition` | `Person.taxCondition` |
| `role` | nombre del rol, string plano |
| `permissions` | lista de códigos de permiso efectivos |
| `iat` / `exp` | emisión y vencimiento |

Antes de agregar, renombrar o quitar un claim: los consumidores son
centauro-pos-manager, centauro-purchase-manager y centauro-pos-front.
Es un cambio coordinado entre repos, nunca unilateral.

Desalineación conocida: este servicio emite `role` como string, pero
pos-manager y purchase-manager leen `roleId.authority` y, al no encontrarlo,
caen al rol `USER`. El front lee `roleId` y tiene fallback a `role`.
Si se arregla, hay que hacerlo en los cuatro repos a la vez.

## Estructura

```
com.sevenb.auth.auth_service
├── configuration/  SecurityConfig, JwtAuthenticationFilter, WebConfig (CORS)
├── controller/     AuthController
├── entity/         entidades JPA y DTOs de API, mezclados
├── exception/      GlobalExceptionHandler, InvalidCredentialsException
├── repository/     UserRepository, RoleRepository, UserPermissionOverrideRepository
└── service/        JwtService, UserService
```

`entity/` mezcla entidades JPA (`User`, `Person`, `Role`, `PermissionEntity`,
`UserPermissionOverride`) con contratos REST (`AuthRequest`, `AuthResponse`,
`ErrorResponse`). No hay paquete `dto/`.

Si se agregan endpoints nuevos, crear `dto/` y no seguir ampliando `entity/`.

## Límites

NO implementar lógica de negocio del POS acá. Este servicio solo autentica.

Ojo: la API de usuarios, roles y permisos que consume el front
(`VITE_ACCESS_API_URL`) **no está en este repositorio**, aunque el modelo de
datos RBAC sí viva acá. No asumir que agregando un controller acá se resuelve
esa pantalla; confirmar primero dónde vive esa API.

## Seguridad

Contraseñas con BCrypt, verificadas por `DaoAuthenticationProvider`.
Nunca comparar contraseñas a mano ni guardar texto plano.

Sesión stateless, CSRF deshabilitado.

Rutas públicas: `/v1/auth/login` y `/v1/auth/register`.
`/v1/auth/register` está declarado público pero el endpoint no existe.

`JwtAuthenticationFilter` valida `Authorization: Bearer <token>` y, ante un
token ilegible, limpia el contexto y sigue la cadena. Las rutas protegidas
terminan en 401 desde el entry point de `SecurityConfig`.

CORS sólo sobre `/v1/auth/**`, con origen `${auth.service.recipeFrontUtl}`
(variable `FRONT_URL`). El nombre de la propiedad tiene un typo histórico;
no lo cambies sin actualizar el entorno desplegado.

## Modelo de datos

- `User` (`users`): username, password, enabled; `@ManyToOne` a `Person` y a `Role`. Implementa `UserDetails`.
- `Person` (`people`): datos de la empresa/persona. `tenant_id` NOT NULL y UNIQUE. Tiene `tax_condition`.
- `Role` (`roles`): `tenant_id` NOT NULL, nombre único por tenant, `@ManyToMany` EAGER con permisos.
- `PermissionEntity` (`permissions`): PK es el `code` string.
- `UserPermissionOverride`: concede o revoca un permiso puntual a un usuario.

`tenantId` vive en `Person` y en `Role`, no en `User`. Al JWT llega desde
`Person`.

No hay multitenancy en runtime: no existe `TenantContextHolder` ni filtros por
tenant, y `findByUsername` es global. Si agregás consultas, considerá
explícitamente el tenant.

## Esquema

Sin Flyway. `spring.jpa.hibernate.ddl-auto=validate`: Hibernate no crea ni
modifica nada y la aplicación no arranca si el esquema no coincide.

Los cambios de esquema son SQL aplicado por fuera. Hay un script suelto en
`src/main/resources/db/manual/`, que no lo ejecuta nadie automáticamente.

Si cambiás una entidad, dejá el SQL correspondiente en `db/manual/` con el
mismo criterio de nombres y avisá que hay que aplicarlo a mano.

## Errores

`InvalidCredentialsException` → 401 con `{"message": "Credenciales inválidas"}`.
Los 401/403 de la cadena de seguridad devuelven el mismo formato desde
`SecurityConfig`.

No hay handlers de validación, 404 ni 500. Si agregás endpoints, ampliá
`GlobalExceptionHandler` en vez de devolver errores a mano.

## Configuración

Variables de entorno: `URI_DB`, `USER_DB`, `PASSWORD_DB`, `FRONT_URL`.

El secreto del JWT **no** es configurable: está en el código.

`spring.jpa.show-sql=true` está activo por defecto.

## Cambios

Antes de modificar:

1. Analizar la implementación existente.
2. Si tocás el JWT, listar qué servicios consumen ese claim.
3. Identificar impacto de esquema (no hay migraciones automáticas).
4. Implementar.
5. Ejecutar tests.

No refactorizar código no relacionado dentro del mismo cambio.

## Validación

El comando que sirve como gate, sin base de datos:

```
./mvnw test -Dtest='!AuthServiceApplicationTests'
```

Hoy pasa: 3 tests verdes (`JwtServiceTest`, `AuthControllerTest`).

`./mvnw test` a secas falla **solo** en `AuthServiceApplicationTests.contextLoads`,
que levanta el contexto completo y necesita `URI_DB`, `USER_DB` y
`PASSWORD_DB` apuntando a una base real. Es una falla de entorno.

Al tocar `JwtService`, agregar o ajustar aserciones en `JwtServiceTest`:
es la única red de seguridad sobre los claims.

## Deuda conocida

- `target/` está versionado en git pese a estar en `.gitignore`. No agregar más binarios; no incluir cambios de `target/` en un commit.
- `.gitignore` tiene entradas duplicadas.
- `RoleRepository` no lo usa nadie.
- `spring-boot-starter-validation` está declarado pero no hay un solo `@Valid`.
- El typo `recipeFrontUtl` en la propiedad de CORS.
