-- Catálogo de permisos nuevos (RBAC) y backfill de los roles existentes de todos los tenants.
--
-- Se aplica A MANO (no hay Flyway en auth-service ni en user-manager). Es idempotente:
-- se puede ejecutar más de una vez sin duplicar filas ni pisar asignaciones existentes.
--
-- Tablas: permissions(code, description), roles(id, tenant_id, name),
--         role_permissions(role_id, permission_code).
-- Los nombres de rol se comparan sin distinguir mayúsculas (mismo criterio que el bypass de ADMIN).

BEGIN;

-- 1) Permisos nuevos del catálogo.
INSERT INTO permissions (code, description)
SELECT v.code, v.description
FROM (VALUES
    ('SALES_DISCOUNT',           'Aplicar descuentos en ventas'),
    ('SALES_EXPORT',             'Exportar ventas'),
    ('SALES_AFIP_RETRY',         'Reintentar autorización ARCA'),
    ('CASH_HISTORY_VIEW',        'Ver historial de cajas'),
    ('STOCK_VIEW',               'Ver stock y remitos'),
    ('STOCK_ADJUST',             'Ajustar stock y gestionar remitos'),
    ('CLIENTS_ACCOUNT_MANAGE',   'Registrar cobros y ajustes en cuenta corriente de clientes'),
    ('SUPPLIERS_ACCOUNT_MANAGE', 'Registrar pagos, cargos y ajustes en cuenta corriente de proveedores'),
    ('PURCHASES_VIEW',           'Ver compras'),
    ('PURCHASES_EDIT',           'Crear, editar y anular compras'),
    ('PURCHASES_CONFIRM',        'Confirmar compras y aplicar stock'),
    ('USERS_MANAGE',             'Gestionar usuarios'),
    ('ROLES_MANAGE',             'Gestionar roles y permisos')
) AS v(code, description)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = v.code);

-- 2) SALES_VIEW pasa a significar "Ver ventas".
UPDATE permissions
SET description = 'Ver ventas'
WHERE code = 'SALES_VIEW'
  AND description IS DISTINCT FROM 'Ver ventas';

-- 3) ADMIN: todos los códigos de la tabla permissions, excepto PLATFORM_*.
--    PLATFORM_* nunca se asigna por rol (solo por override); este script manual se puede volver
--    a ejecutar después de V20261011__platform_admin.sql y no debe reasignarlo a ADMIN.
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM roles r
CROSS JOIN permissions p
WHERE UPPER(r.name) = 'ADMIN'
  AND p.code NOT LIKE 'PLATFORM\_%'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_code = p.code
  );

-- 4) SUPERVISOR: los permisos nuevos, excepto USERS_MANAGE y ROLES_MANAGE.
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, v.code
FROM roles r
CROSS JOIN (VALUES
    ('SALES_DISCOUNT'),
    ('SALES_EXPORT'),
    ('SALES_AFIP_RETRY'),
    ('CASH_HISTORY_VIEW'),
    ('STOCK_VIEW'),
    ('STOCK_ADJUST'),
    ('CLIENTS_ACCOUNT_MANAGE'),
    ('SUPPLIERS_ACCOUNT_MANAGE'),
    ('PURCHASES_VIEW'),
    ('PURCHASES_EDIT'),
    ('PURCHASES_CONFIRM')
) AS v(code)
WHERE UPPER(r.name) = 'SUPERVISOR'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_code = v.code
  );

-- 5) CAJERO: reintento de autorización ARCA.
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, 'SALES_AFIP_RETRY'
FROM roles r
WHERE UPPER(r.name) = 'CAJERO'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_code = 'SALES_AFIP_RETRY'
  );

-- 6) Roles personalizados (nombre distinto de ADMIN, SUPERVISOR y CAJERO).
--    Criterio: cada permiso nuevo se deriva del permiso existente que ya cubría esa pantalla o
--    acción, para que nadie pierda acceso cuando el chequeo deje el modo sombra:
--      SUPPLIERS_EDIT       -> PURCHASES_VIEW, PURCHASES_EDIT, PURCHASES_CONFIRM, SUPPLIERS_ACCOUNT_MANAGE
--      SUPPLIERS_VIEW       -> PURCHASES_VIEW
--      PRODUCTS_EDIT        -> STOCK_VIEW, STOCK_ADJUST
--      PRODUCTS_VIEW        -> STOCK_VIEW
--      CLIENTS_EDIT         -> CLIENTS_ACCOUNT_MANAGE
--      SALES_VIEW           -> SALES_EXPORT, SALES_AFIP_RETRY
--    Nunca se derivan USERS_MANAGE ni ROLES_MANAGE.
INSERT INTO role_permissions (role_id, permission_code)
SELECT DISTINCT r.id, m.target_code
FROM roles r
JOIN role_permissions src ON src.role_id = r.id
JOIN (VALUES
    ('SUPPLIERS_EDIT', 'PURCHASES_VIEW'),
    ('SUPPLIERS_EDIT', 'PURCHASES_EDIT'),
    ('SUPPLIERS_EDIT', 'PURCHASES_CONFIRM'),
    ('SUPPLIERS_EDIT', 'SUPPLIERS_ACCOUNT_MANAGE'),
    ('SUPPLIERS_VIEW', 'PURCHASES_VIEW'),
    ('PRODUCTS_EDIT',  'STOCK_VIEW'),
    ('PRODUCTS_EDIT',  'STOCK_ADJUST'),
    ('PRODUCTS_VIEW',  'STOCK_VIEW'),
    ('CLIENTS_EDIT',   'CLIENTS_ACCOUNT_MANAGE'),
    ('SALES_VIEW',     'SALES_EXPORT'),
    ('SALES_VIEW',     'SALES_AFIP_RETRY')
) AS m(source_code, target_code) ON m.source_code = src.permission_code
WHERE UPPER(r.name) NOT IN ('ADMIN', 'SUPERVISOR', 'CAJERO')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_code = m.target_code
  );

--    CASH_SESSIONS_MANAGE + REPORTS_VIEW -> CASH_HISTORY_VIEW (requiere ambos).
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, 'CASH_HISTORY_VIEW'
FROM roles r
WHERE UPPER(r.name) NOT IN ('ADMIN', 'SUPERVISOR', 'CAJERO')
  AND EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_code = 'CASH_SESSIONS_MANAGE'
  )
  AND EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_code = 'REPORTS_VIEW'
  )
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_code = 'CASH_HISTORY_VIEW'
  );

COMMIT;
