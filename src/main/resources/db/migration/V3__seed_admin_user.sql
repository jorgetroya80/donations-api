-- Default admin user: username=admin, password=admin (change immediately after first setup)
-- BCrypt hash of "admin"
INSERT INTO users (username, password, active, created_by, created_at, updated_by, updated_at)
VALUES ('admin', '$2b$10$MBA8LCeKgZwK3AxpgVL8/Oh1tB0Ch/leKOQODww4xDV/FwQrn1ga6', TRUE, 'system', NOW(), 'system', NOW());

INSERT INTO user_roles (user_id, role)
VALUES (1, 'ADMIN');
