-- Permiso de administrador de la plataforma Centauro (PLATFORM_ADMIN).
--
-- Se aplica A MANO (no hay Flyway en auth-service ni en user-manager). Es idempotente.
--
-- PLATFORM_* nunca se asigna por rol: solo por override concedido (user_permission_overrides,
-- granted = true) a usuarios puntuales del tenant propio de Centauro. auth-service descarta
-- cualquier PLATFORM_* que venga del rol al emitir el JWT.

BEGIN;

-- 1) Código nuevo del catálogo.
INSERT INTO permissions (code, description)
SELECT 'PLATFORM_ADMIN', 'Administrador de la plataforma Centauro'
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = 'PLATFORM_ADMIN');

-- 2) Red de seguridad: el backfill de ADMIN (V20261010, paso 3) asigna todos los códigos de la
--    tabla permissions; si se ejecutó después del paso 1, ADMIN quedó con PLATFORM_ADMIN.
DELETE FROM role_permissions WHERE permission_code LIKE 'PLATFORM\_%';

COMMIT;

-- 3) Ejemplo (comentado): conceder PLATFORM_ADMIN a un usuario puntual del tenant de Centauro.
--    Reemplazar 'usuario.centauro' y '<TENANT_CENTAURO>'. El filtro por tenant evita concederlo a
--    un usuario homónimo de otra empresa.
--
-- INSERT INTO user_permission_overrides (user_id, permission_code, granted)
-- SELECT u.id, 'PLATFORM_ADMIN', true
-- FROM users u
-- JOIN people p ON p.id = u.person_id AND p.tenant_id = '<TENANT_CENTAURO>'
-- WHERE u.username = 'usuario.centauro'
--   AND NOT EXISTS (
--       SELECT 1 FROM user_permission_overrides o
--       WHERE o.user_id = u.id AND o.permission_code = 'PLATFORM_ADMIN'
--   );
--
--    Si ya existía un override revocado (granted = false), el INSERT no hace nada; activarlo con:
-- UPDATE user_permission_overrides o
-- SET granted = true
-- FROM users u
-- JOIN people p ON p.id = u.person_id AND p.tenant_id = '<TENANT_CENTAURO>'
-- WHERE o.user_id = u.id
--   AND u.username = 'usuario.centauro'
--   AND o.permission_code = 'PLATFORM_ADMIN';
--
--    Revocar (granted = false o borrar el override) no corta el acceso al instante: los JWT ya
--    emitidos conservan PLATFORM_ADMIN hasta vencer, hasta 5 días (vida del token).
