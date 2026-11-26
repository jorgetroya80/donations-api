# Plan: Test data seed script

## Architectural decisions

- **Delivery**: standalone SQL script (`src/test/resources/seed_test_data.sql`), NOT a Flyway migration — safe to delete without breaking `flyway_schema_history`.
- **DNI format**: 100 sequential valid Spanish DNIs `10000000Z`–`10000099K`, checksum verified against `NationalIdValidator` (`TRWAGMYFPDXBNJZSQVHLCKE[n % 23]`).
- **Payment method (tithes)**: derived deterministically from DNI number — `even % 2 → CASH`, `odd % 2 → BANK_TRANSFER`. Fixed per donor across all months.
- **Donation date (tithes)**: derived from `DNI_number % 4` → day 5, 10, 15, or 20. Spreads dates within the month.
- **Amounts**: `RANDOM()` at INSERT time — vary on each run (acceptable for test data).
- **Offerings**: linked to 5 specific donors spread across the list; payment method random per donation.
- **Run command**: `psql -U postgres -d donations -f src/test/resources/seed_test_data.sql`
- **Precondition**: `donors`, `donations`, and `expenses` tables must be empty. Re-running on non-empty DB fails on `national_id` UNIQUE constraint.

---

## Phase 1: Generate seed script

### What was built

Single SQL script wrapping all inserts in one transaction (`BEGIN` / `COMMIT`).

| Section | Rows | Notes |
|---------|------|-------|
| Donors | 100 | 50 male + 50 female, realistic Spanish names |
| Tithe donations | 600 | 100 donors × 6 months (Jan–Jun 2026), 100–150 € |
| Offering donations | 6 | One per month Jan–Jun, 20–50 €, spread across donors |
| Monthly rent expenses | 5 | 1 500 €, BANK_TRANSFER, first days of each month |
| Weekly expenses | 21 | Alternating MAINTENANCE/SUPPLIES, 8–12 €, CASH |
| Monthly electricity expenses | 5 | 250–300 €, BANK_TRANSFER, day 5 each month, vendor Endesa |
| Monthly water expenses | 5 | 50–70 €, BANK_TRANSFER, day 5 each month, vendor Canal de Isabel II |
| IRPF quarterly expenses | 2 | 30% of all donations per quarter; Q1 paid Apr 20, Q2 paid Jul 20 |

### Acceptance criteria

- [ ] Script runs cleanly on empty DB: `psql ... -f src/test/resources/seed_test_data.sql` exits 0
- [ ] `SELECT COUNT(*) FROM donors` → 100
- [ ] `SELECT COUNT(*) FROM donations WHERE donation_type = 'TITHE'` → 600
- [ ] `SELECT COUNT(*) FROM donations WHERE donation_type = 'OFFERING'` → 6
- [ ] `SELECT COUNT(*) FROM expenses WHERE category = 'RENT'` → 5
- [ ] `SELECT COUNT(*) FROM expenses WHERE category IN ('MAINTENANCE','SUPPLIES')` → 21
- [ ] `SELECT COUNT(*) FROM expenses WHERE category = 'UTILITIES' AND description = 'Monthly electricity'` → 5
- [ ] `SELECT COUNT(*) FROM expenses WHERE category = 'UTILITIES' AND description = 'Monthly water supply'` → 5
- [ ] `SELECT COUNT(*) FROM expenses WHERE category = 'IRPF'` → 2
- [ ] All donor `national_id` values pass `NationalIdValidator` (8 digits + correct checksum letter)
- [ ] No donor has a `national_id` that starts with a letter (all are DNI, not NIE)
