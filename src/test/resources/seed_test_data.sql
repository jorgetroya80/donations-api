-- ============================================================
-- Test data seed script
-- Run: psql -U postgres -d donations -f src/test/resources/seed_test_data.sql
-- Assumes donors, donations, and expenses tables are empty.
-- NOT a Flyway migration — safe to delete at any time.
-- Re-running on a non-empty DB will fail on duplicate national_id.
-- ============================================================

BEGIN;

-- ============================================================
-- 1. DONORS (100: 50 male + 50 female, valid Spanish DNIs)
-- ============================================================

INSERT INTO donors (full_name, national_id, active) VALUES
('Antonio García',      '10000000Z', true),
('Manuel Martínez',     '10000001S', true),
('José López',          '10000002Q', true),
('Francisco Sánchez',   '10000003V', true),
('David González',      '10000004H', true),
('Juan Pérez',          '10000005L', true),
('Javier Rodríguez',   '10000006C', true),
('Carlos Fernández',    '10000007K', true),
('Daniel Gómez',        '10000008E', true),
('Miguel Torres',       '10000009T', true),
('Pedro Díaz',          '10000010R', true),
('Alejandro Álvarez',   '10000011W', true),
('Luis Romero',         '10000012A', true),
('Sergio Alonso',       '10000013G', true),
('Fernando Gutiérrez', '10000014M', true),
('Pablo Navarro',       '10000015Y', true),
('Jorge Moreno',        '10000016F', true),
('Alberto Jiménez',     '10000017P', true),
('Óscar Castro',        '10000018D', true),
('Raúl Ruiz',           '10000019X', true),
('Adrián Ortega',       '10000020B', true),
('Rubén Delgado',       '10000021N', true),
('Iván Vargas',         '10000022J', true),
('Gonzalo Herrera',     '10000023Z', true),
('Rafael Molina',       '10000024S', true),
('Marcos Medina',       '10000025Q', true),
('Víctor Blanco',       '10000026V', true),
('Diego Suárez',        '10000027H', true),
('Ignacio Morales',     '10000028L', true),
('Enrique Vega',        '10000029C', true),
('Eduardo Ramos',       '10000030K', true),
('Roberto Flores',      '10000031E', true),
('Tomás Iglesias',      '10000032T', true),
('Jesús Serrano',       '10000033R', true),
('Rodrigo Cortés',      '10000034W', true),
('Álvaro Domínguez',   '10000035A', true),
('Emilio Fuentes',      '10000036G', true),
('Félix Cruz',          '10000037M', true),
('Ramón Reyes',         '10000038Y', true),
('Germán Ríos',         '10000039F', true),
('Andrés Núñez',        '10000040P', true),
('Cristóbal Pinto',     '10000041D', true),
('Sebastián Rubio',     '10000042X', true),
('Nicolás Caballero',   '10000043B', true),
('Lorenzo Carmona',     '10000044N', true),
('Salvador Lozano',     '10000045J', true),
('Ángel Mendoza',       '10000046Z', true),
('Mateo Guerrero',      '10000047S', true),
('César Calvo',         '10000048Q', true),
('Hugo Moya',           '10000049V', true),
('María García',        '10000050H', true),
('Ana Martínez',        '10000051L', true),
('Carmen López',        '10000052C', true),
('Laura Sánchez',       '10000053K', true),
('Isabel González',     '10000054E', true),
('Sara Pérez',          '10000055T', true),
('Lucía Rodríguez',    '10000056R', true),
('Elena Fernández',     '10000057W', true),
('Cristina Gómez',      '10000058A', true),
('Marta Torres',        '10000059G', true),
('Rosa Cano',           '10000060M', true),
('Patricia Álvarez',    '10000061Y', true),
('Paula Romero',        '10000062F', true),
('Natalia Alonso',      '10000063P', true),
('Silvia Gutiérrez',   '10000064D', true),
('Sofía Navarro',       '10000065X', true),
('Andrea Moreno',       '10000066B', true),
('Teresa Jiménez',      '10000067N', true),
('Raquel Castro',       '10000068J', true),
('Beatriz Ruiz',        '10000069Z', true),
('Alicia Ortega',       '10000070S', true),
('Pilar Delgado',       '10000071Q', true),
('Gloria Vargas',       '10000072V', true),
('Yolanda Herrera',     '10000073H', true),
('Rocío Molina',        '10000074L', true),
('Nuria Medina',        '10000075C', true),
('Esther Blanco',       '10000076K', true),
('Sandra Suárez',       '10000077E', true),
('Claudia Morales',     '10000078T', true),
('Verónica Vega',       '10000079R', true),
('Lourdes Ramos',       '10000080W', true),
('Esperanza Flores',    '10000081A', true),
('Dolores Iglesias',    '10000082G', true),
('Mónica Serrano',      '10000083M', true),
('Amparo Cortés',       '10000084Y', true),
('Irene Domínguez',     '10000085F', true),
('Julia Fuentes',       '10000086P', true),
('Rebeca Cruz',         '10000087D', true),
('Valentina Reyes',     '10000088X', true),
('Mercedes Ríos',       '10000089B', true),
('Consuelo Núñez',      '10000090N', true),
('Encarna Pinto',       '10000091J', true),
('Inmaculada Rubio',    '10000092Z', true),
('Remedios Caballero',  '10000093S', true),
('Asunción Carmona',    '10000094Q', true),
('Francisca Lozano',    '10000095V', true),
('Josefa Mendoza',      '10000096H', true),
('Rosario Guerrero',    '10000097L', true),
('Concepción Calvo',    '10000098C', true),
('Milagros Moya',       '10000099K', true);

-- ============================================================
-- 2. TITHE DONATIONS (100 donors × 5 months Jan–May 2026 = 500)
-- Payment method: derived from national_id number (even=CASH, odd=BANK_TRANSFER)
-- Date: derived from national_id number mod 4 (day 5, 10, 15, or 20)
-- Amount: random 100–150 EUR
-- ============================================================

INSERT INTO donations (amount, donation_date, donation_type, payment_method, donor_id)
SELECT
    ROUND((100 + FLOOR(RANDOM() * 51))::NUMERIC, 2),
    make_date(2026, m.month,
        CASE (CAST(SUBSTRING(d.national_id FROM 1 FOR 8) AS INTEGER) % 4)
            WHEN 0 THEN 5
            WHEN 1 THEN 10
            WHEN 2 THEN 15
            ELSE 20
        END),
    'TITHE',
    CASE WHEN CAST(SUBSTRING(d.national_id FROM 1 FOR 8) AS INTEGER) % 2 = 0
         THEN 'CASH'
         ELSE 'BANK_TRANSFER'
    END,
    d.id
FROM donors d
CROSS JOIN (VALUES (1), (2), (3), (4), (5)) AS m(month)
ORDER BY d.national_id, m.month;

-- ============================================================
-- 3. OFFERING DONATIONS (one per month, linked to a specific donor)
-- Amount: random 20–50 EUR, payment method: random
-- ============================================================

INSERT INTO donations (amount, donation_date, donation_type, payment_method, donor_id)
SELECT
    ROUND((20 + FLOOR(RANDOM() * 31))::NUMERIC, 2),
    t.offer_date,
    'OFFERING',
    CASE WHEN RANDOM() < 0.5 THEN 'CASH' ELSE 'BANK_TRANSFER' END,
    (SELECT id FROM donors WHERE national_id = t.nid)
FROM (VALUES
    ('2026-01-25'::DATE, '10000010R'),
    ('2026-02-22'::DATE, '10000035A'),
    ('2026-03-29'::DATE, '10000060M'),
    ('2026-04-26'::DATE, '10000075C'),
    ('2026-05-24'::DATE, '10000090N')
) AS t(offer_date, nid);

-- ============================================================
-- 3b. JUNE TITHE DONATIONS (100 donors, extends Q2 to full quarter)
-- ============================================================

INSERT INTO donations (amount, donation_date, donation_type, payment_method, donor_id)
SELECT
    ROUND((100 + FLOOR(RANDOM() * 51))::NUMERIC, 2),
    make_date(2026, 6,
        CASE (CAST(SUBSTRING(d.national_id FROM 1 FOR 8) AS INTEGER) % 4)
            WHEN 0 THEN 5
            WHEN 1 THEN 10
            WHEN 2 THEN 15
            ELSE 20
        END),
    'TITHE',
    CASE WHEN CAST(SUBSTRING(d.national_id FROM 1 FOR 8) AS INTEGER) % 2 = 0
         THEN 'CASH'
         ELSE 'BANK_TRANSFER'
    END,
    d.id
FROM donors d
ORDER BY d.national_id;

-- ============================================================
-- 3c. JUNE OFFERING DONATION (one, completes Q2 offerings)
-- ============================================================

INSERT INTO donations (amount, donation_date, donation_type, payment_method, donor_id)
SELECT
    ROUND((20 + FLOOR(RANDOM() * 31))::NUMERIC, 2),
    '2026-06-28'::DATE,
    'OFFERING',
    CASE WHEN RANDOM() < 0.5 THEN 'CASH' ELSE 'BANK_TRANSFER' END,
    (SELECT id FROM donors WHERE national_id = '10000045J');

-- ============================================================
-- 4. MONTHLY RENT EXPENSES (one per month, first week Jan–May 2026)
-- ============================================================

INSERT INTO expenses (amount, expense_date, category, description, vendor, payment_method) VALUES
(1500.00, '2026-01-03', 'RENT', 'Monthly rent', 'Propietario local', 'BANK_TRANSFER'),
(1500.00, '2026-02-03', 'RENT', 'Monthly rent', 'Propietario local', 'BANK_TRANSFER'),
(1500.00, '2026-03-03', 'RENT', 'Monthly rent', 'Propietario local', 'BANK_TRANSFER'),
(1500.00, '2026-04-03', 'RENT', 'Monthly rent', 'Propietario local', 'BANK_TRANSFER'),
(1500.00, '2026-05-03', 'RENT', 'Monthly rent', 'Propietario local', 'BANK_TRANSFER');

-- ============================================================
-- 5. WEEKLY EXPENSES (21 weeks Jan–May 2026, alternating MAINTENANCE/SUPPLIES)
-- Amount: random 8–12 EUR, payment method: CASH
-- ============================================================

INSERT INTO expenses (amount, expense_date, category, description, vendor, payment_method)
SELECT
    ROUND((8 + RANDOM() * 4)::NUMERIC, 2),
    t.expense_date,
    t.category,
    CASE t.category WHEN 'MAINTENANCE' THEN 'Weekly maintenance' ELSE 'Weekly supplies' END,
    'Supermarket',
    'CASH'
FROM (VALUES
    ('2026-01-05'::DATE, 'MAINTENANCE'),
    ('2026-01-12'::DATE, 'SUPPLIES'),
    ('2026-01-19'::DATE, 'MAINTENANCE'),
    ('2026-01-26'::DATE, 'SUPPLIES'),
    ('2026-02-02'::DATE, 'MAINTENANCE'),
    ('2026-02-09'::DATE, 'SUPPLIES'),
    ('2026-02-16'::DATE, 'MAINTENANCE'),
    ('2026-02-23'::DATE, 'SUPPLIES'),
    ('2026-03-02'::DATE, 'MAINTENANCE'),
    ('2026-03-09'::DATE, 'SUPPLIES'),
    ('2026-03-16'::DATE, 'MAINTENANCE'),
    ('2026-03-23'::DATE, 'SUPPLIES'),
    ('2026-03-30'::DATE, 'MAINTENANCE'),
    ('2026-04-06'::DATE, 'SUPPLIES'),
    ('2026-04-13'::DATE, 'MAINTENANCE'),
    ('2026-04-20'::DATE, 'SUPPLIES'),
    ('2026-04-27'::DATE, 'MAINTENANCE'),
    ('2026-05-04'::DATE, 'SUPPLIES'),
    ('2026-05-11'::DATE, 'MAINTENANCE'),
    ('2026-05-18'::DATE, 'SUPPLIES'),
    ('2026-05-25'::DATE, 'MAINTENANCE')
) AS t(expense_date, category);

-- ============================================================
-- 6. MONTHLY ELECTRICITY EXPENSES (one per month, Jan–May 2026)
-- ============================================================

INSERT INTO expenses (amount, expense_date, category, description, vendor, payment_method)
SELECT
    ROUND((250 + RANDOM() * 50)::NUMERIC, 2),
    t.expense_date,
    'UTILITIES',
    'Monthly electricity',
    'Endesa',
    'BANK_TRANSFER'
FROM (VALUES
    ('2026-01-05'::DATE),
    ('2026-02-05'::DATE),
    ('2026-03-05'::DATE),
    ('2026-04-05'::DATE),
    ('2026-05-05'::DATE)
) AS t(expense_date);

-- ============================================================
-- 7. MONTHLY WATER EXPENSES (one per month, Jan–May 2026)
-- ============================================================

INSERT INTO expenses (amount, expense_date, category, description, vendor, payment_method)
SELECT
    ROUND((50 + RANDOM() * 20)::NUMERIC, 2),
    t.expense_date,
    'UTILITIES',
    'Monthly water supply',
    'Canal de Isabel II',
    'BANK_TRANSFER'
FROM (VALUES
    ('2026-01-05'::DATE),
    ('2026-02-05'::DATE),
    ('2026-03-05'::DATE),
    ('2026-04-05'::DATE),
    ('2026-05-05'::DATE)
) AS t(expense_date);

-- ============================================================
-- 8. IRPF QUARTERLY TAX (Q1 Apr-20, Q2 Jul-20 — 30% of all donations)
-- Must run after all donation inserts.
-- ============================================================

INSERT INTO expenses (amount, expense_date, category, description, vendor, payment_method)
SELECT
    ROUND((SELECT SUM(amount) * 0.30 FROM donations
           WHERE donation_date BETWEEN q.start_date AND q.end_date), 2),
    q.payment_date,
    'IRPF',
    'IRPF quarterly payment',
    'Agencia Tributaria',
    'BANK_TRANSFER'
FROM (VALUES
    ('2026-01-01'::DATE, '2026-03-31'::DATE, '2026-04-20'::DATE),
    ('2026-04-01'::DATE, '2026-06-30'::DATE, '2026-07-20'::DATE)
) AS q(start_date, end_date, payment_date);

COMMIT;
