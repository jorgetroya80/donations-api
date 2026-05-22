# PRD: Rename `dni_nie` to `national_id` for International Generality

## Problem Statement

The `donors` table stores a government-issued national identifier in a field called `dni_nie`, named after Spanish document types (DNI = Documento Nacional de Identidad, NIE = Número de Identificación de Extranjero). This naming tightly couples the data model to Spain, making it semantically incorrect and confusing if the system is ever used in another country or extended to handle donors from multiple countries.

## Solution

Rename the `dni_nie` column (and all corresponding code references) to `national_id` — a neutral, internationally understood term for a government-issued national identifier. The Spanish DNI/NIE validation logic is preserved but decoupled from the field name by renaming the validation annotation to `@ValidNationalId`.

## User Stories

1. As a developer, I want the donor national identifier field to have a generic name, so that the data model is not tied to Spanish-specific terminology.
2. As a developer, I want the Kotlin entity field to be named `nationalId`, so that the code is consistent with the renamed database column.
3. As a developer, I want the API request and response JSON to use `nationalId` instead of `dniNie`, so that the API surface reflects the generic field name.
4. As a developer, I want the validation annotation to be named `@ValidNationalId`, so that it can logically apply to any national ID field regardless of country.
5. As a developer, I want the existing Spanish DNI/NIE checksum validation logic to remain unchanged, so that current donors are still validated correctly.
6. As a developer, I want the repository method to be named `existsByNationalId`, so that it follows Spring Data naming conventions for the renamed field.
7. As a developer, I want all error messages referencing "DNI/NIE" to say "national ID" instead, so that error output is consistent with the renamed field.
8. As a developer, I want all existing tests to continue passing after the rename, so that I have confidence the change is purely cosmetic with no behaviour change.
9. As a developer, I want the database migration script to reflect `national_id` as the column name, so that fresh database setups use the correct name from the start.

## Implementation Decisions

### Database
- The `donors` table column is renamed from `dni_nie` to `national_id`.
- Because no production data exists, the V4 migration (`V4__create_donors.sql`) is edited in place rather than adding a new migration file. This avoids unnecessary migration history for a pre-release rename.

### Donor Entity
- The JPA entity field `dniNie` is renamed to `nationalId`.
- The `@Column(name = "dni_nie")` annotation is updated to `@Column(name = "national_id")`.

### DTOs
- `CreateDonorRequest`: field `dniNie` → `nationalId`; validation message updated to `"National ID is required"`.
- `UpdateDonorRequest`: field `dniNie` → `nationalId`.
- `DonorResponse`: field `dniNie` → `nationalId`; mapping in `from()` companion updated accordingly.

### Repository
- Spring Data method `existsByDniNie` → `existsByNationalId`.

### Service
- All field accesses, variable names, and duplicate-check error messages updated from `dniNie`/`"DNI/NIE"` → `nationalId`/`"national ID"`.

### Validation
- Annotation file renamed: `ValidDniNie.kt` → `ValidNationalId.kt`; annotation class renamed to `ValidNationalId`; `validatedBy` updated to reference renamed validator; default message updated to `"Invalid national ID format"`.
- Validator file renamed: `DniNieValidator.kt` → `NationalIdValidator.kt`; class renamed to `NationalIdValidator`; `ConstraintValidator` type parameter updated to `ValidNationalId`; internal DNI/NIE checksum logic is **unchanged**.

### API Contract
- JSON field name changes from `dniNie` to `nationalId` in all request and response bodies. This is a breaking change to the API contract, accepted because there is no production release yet.

## Testing Decisions

- Tests should validate external behaviour only: what comes in via HTTP, what goes out via HTTP, and what error messages are returned. Internal field names are not directly tested.
- All three integration test classes (`DonorManagementTest`, `DonationRecordingTest`, `FinancialReportsTest`) use `nationalId` as the JSON key in request payloads and assert on `nationalId` in response bodies.
- No new test cases are needed — this is a rename with no behaviour change. All existing test scenarios (valid DNI accepted, valid NIE accepted, duplicate rejected, invalid format rejected, update flow) continue to cover the renamed field.
- Tests follow the existing Testcontainers + Spring Boot Test pattern already in the codebase.

## Out of Scope

- **Country-specific validation**: routing validation logic based on a donor's country (e.g. Spanish DNI/NIE rules for Spain, different rules elsewhere) is explicitly excluded. This requires adding a `country` field to the `donors` table, which is a separate concern.
- **Adding a `country` field**: not part of this change.
- **Adding a new Flyway migration (V5)**: not needed since there is no production data to migrate.
- **Changing validation logic**: the Spanish DNI/NIE checksum algorithm is preserved exactly as-is.

## Further Notes

- Once a `country` field is added to the donors table in a future PRD, the `@ValidNationalId` validator can be extended to dispatch country-specific rules without changing the field name or annotation name.
- If a production release is ever cut before this change is merged, the approach should switch from editing V4 in place to adding a V5 `ALTER TABLE` migration.
