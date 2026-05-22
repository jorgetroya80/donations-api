# Plan: Rename `dni_nie` to `national_id`

> Source PRD: [docs/PRD-6.md](../docs/PRD-6.md) · [GitHub issue #23](https://github.com/jorgetroya80/donations-api/issues/23)

## Architectural decisions

- **Schema**: `donors.dni_nie` → `donors.national_id` (VARCHAR 20, NOT NULL, UNIQUE). V4 migration edited in place — no V5 needed (no production data).
- **Key model**: `Donor.nationalId: String` mapped to `national_id` column.
- **Validation**: `@ValidNationalId` annotation + `NationalIdValidator` class. Spanish DNI/NIE checksum logic unchanged.
- **API contract**: JSON field `dniNie` → `nationalId` in all request/response bodies. Breaking change accepted — no production release yet.
- **Spring Data**: repository method `existsByNationalId` derived from field name.

---

## Phase 1: Core rename — schema, entity, validation

**User stories**: 1, 2, 4, 5, 9

### What to build

Rename the field at its source: the database column, the JPA entity, and the validation layer. After this phase the core model is consistent end-to-end. The API surface (DTOs, repository, service) will be updated in Phase 2.

- Edit `V4__create_donors.sql`: column name `dni_nie` → `national_id`.
- Update `Donor` entity: field `dniNie` → `nationalId`, `@Column(name = "dni_nie")` → `@Column(name = "national_id")`.
- Rename validation annotation file `ValidDniNie.kt` → `ValidNationalId.kt`: class `ValidDniNie` → `ValidNationalId`, `validatedBy` updated, default message → `"Invalid national ID format"`.
- Rename validator file `DniNieValidator.kt` → `NationalIdValidator.kt`: class `NationalIdValidator`, `ConstraintValidator` type param → `ValidNationalId`. Internal DNI/NIE checksum logic untouched.

### Acceptance criteria

- [ ] `V4__create_donors.sql` uses `national_id` as the column name
- [ ] `Donor.nationalId` maps to `national_id` column via `@Column`
- [ ] `@ValidNationalId` annotation exists; `@ValidDniNie` does not
- [ ] `NationalIdValidator` implements `ConstraintValidator<ValidNationalId, String>`
- [ ] Spanish DNI/NIE checksum logic in `NationalIdValidator` is identical to the original

---

## Phase 2: API surface + tests

**User stories**: 3, 6, 7, 8

### What to build

Update every layer that references the renamed field so the codebase compiles and all tests pass.

- **DTOs** (`DonorDtos.kt`): `dniNie` → `nationalId` in `CreateDonorRequest`, `UpdateDonorRequest`, `DonorResponse`, and `DonorResponse.from()`. Swap `@ValidDniNie` → `@ValidNationalId`. Update validation message to `"National ID is required"`.
- **Repository** (`DonorRepository.kt`): `existsByDniNie` → `existsByNationalId`.
- **Service** (`DonorService.kt`): all `dniNie` field accesses and variable names → `nationalId`; `existsByDniNie` → `existsByNationalId`; error messages `"DNI/NIE '...'"` → `"national ID '...'"`.
- **Tests**: in `DonorManagementTest`, `DonationRecordingTest`, `FinancialReportsTest` — JSON key `"dniNie"` → `"nationalId"`, Kotlin field refs and helper method params updated, test method names referencing `dniNie` updated.

### Acceptance criteria

- [ ] Project compiles with no errors
- [ ] `POST /api/v1/donors` accepts `nationalId` in request body
- [ ] `GET /api/v1/donors/{id}` returns `nationalId` in response body
- [ ] Duplicate national ID returns error mentioning `"national ID"`
- [ ] Invalid format returns `"Invalid national ID format"`
- [ ] `./gradlew test` passes with no failures
