CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_donors_full_name_trgm ON donors USING gin (LOWER(full_name) gin_trgm_ops);
CREATE INDEX idx_donors_national_id_trgm ON donors USING gin (LOWER(national_id) gin_trgm_ops);
