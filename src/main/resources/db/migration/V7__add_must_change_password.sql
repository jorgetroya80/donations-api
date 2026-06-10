ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- Force rotation of the seeded default credentials. Only flags the admin
-- account if its password is still the V3 default hash ("admin").
UPDATE users
SET must_change_password = TRUE
WHERE username = 'admin'
  AND password = '$2b$10$MBA8LCeKgZwK3AxpgVL8/Oh1tB0Ch/leKOQODww4xDV/FwQrn1ga6';
