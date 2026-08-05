# Changelog

## [2.1.0](https://github.com/jorgetroya80/donations-api/compare/v2.0.0...v2.1.0) (2026-08-05)


### Features

* structured domain and security event logging ([#48](https://github.com/jorgetroya80/donations-api/issues/48)) ([631a366](https://github.com/jorgetroya80/donations-api/commit/631a366c966b84c281fd2ec70d3e06a015a7923b))

## [2.0.0](https://github.com/jorgetroya80/donations-api/compare/v1.11.0...v2.0.0) (2026-07-16)


### ⚠ BREAKING CHANGES

* error responses are now RFC 9457 application/problem+json (message→detail, error→title, timestamp dropped)

### Features

* adopt RFC 9457 ProblemDetail errors and API consistency fixes ([#45](https://github.com/jorgetroya80/donations-api/issues/45)) ([b95737d](https://github.com/jorgetroya80/donations-api/commit/b95737d0f610d9263e1e8d067fe9cb7b3fa6da92))

## [1.11.0](https://github.com/jorgetroya80/donations-api/compare/v1.10.1...v1.11.0) (2026-06-27)


### Features

* deploy API to Render with Neon ([#42](https://github.com/jorgetroya80/donations-api/issues/42)) ([192c8b8](https://github.com/jorgetroya80/donations-api/commit/192c8b846fe20bf772dec3cbad60df539923b0fc))

## [1.10.1](https://github.com/jorgetroya80/donations-api/compare/v1.10.0...v1.10.1) (2026-06-26)


### Performance Improvements

* **db:** speed up donor search and user role loading ([#40](https://github.com/jorgetroya80/donations-api/issues/40)) ([ca26e1a](https://github.com/jorgetroya80/donations-api/commit/ca26e1a79be23924d16887d2bd145349ad0adc48))

## [1.10.0](https://github.com/jorgetroya80/donations-api/compare/v1.9.0...v1.10.0) (2026-06-22)


### Features

* add search query param to GET /api/v1/donors ([#38](https://github.com/jorgetroya80/donations-api/issues/38)) ([7c63c87](https://github.com/jorgetroya80/donations-api/commit/7c63c871a1964491f0f848d2d92f53caf1c6b1bc))

## [1.9.0](https://github.com/jorgetroya80/donations-api/compare/v1.8.0...v1.9.0) (2026-06-16)


### Features

* add test data seed script ([#35](https://github.com/jorgetroya80/donations-api/issues/35)) ([10c0197](https://github.com/jorgetroya80/donations-api/commit/10c019778e42b13fa2ecf0ccd531ae932f0b8235))

## [1.8.0](https://github.com/jorgetroya80/donations-api/compare/v1.7.1...v1.8.0) (2026-06-12)


### Features

* stable page serialization via PagedModel (VIA_DTO) ([#33](https://github.com/jorgetroya80/donations-api/issues/33)) ([ba2b3b2](https://github.com/jorgetroya80/donations-api/commit/ba2b3b228bfc9e678ec9447c078613ed0f558897))

## [1.7.1](https://github.com/jorgetroya80/donations-api/compare/v1.7.0...v1.7.1) (2026-06-10)


### Bug Fixes

* Security hardening - 8 findings from auth/session review ([#31](https://github.com/jorgetroya80/donations-api/issues/31)) ([f52496e](https://github.com/jorgetroya80/donations-api/commit/f52496e4f13b853eeaad6ccb76870d2c2fbb6732))

## [1.7.0](https://github.com/jorgetroya80/donations-api/compare/v1.6.0...v1.7.0) (2026-05-25)


### Features

* export createClient and createConfig from API client package ([#29](https://github.com/jorgetroya80/donations-api/issues/29)) ([548bbad](https://github.com/jorgetroya80/donations-api/commit/548bbadb3e04d2fd062b425c91b987b1902937ef))

## [1.6.0](https://github.com/jorgetroya80/donations-api/compare/v1.5.0...v1.6.0) (2026-05-22)


### Features

* update docker compose to run API and DB ([#26](https://github.com/jorgetroya80/donations-api/issues/26)) ([3346085](https://github.com/jorgetroya80/donations-api/commit/33460854fb28f6ae76be81eba3e673e855b9ef08))

## [1.5.0](https://github.com/jorgetroya80/donations-api/compare/v1.4.1...v1.5.0) (2026-05-22)


### Features

* rename dni_nie to national_id for international generality ([#24](https://github.com/jorgetroya80/donations-api/issues/24)) ([a3b4fff](https://github.com/jorgetroya80/donations-api/commit/a3b4fff2f00ea97fa1bdbbc446418294dde57be9))

## [1.4.1](https://github.com/jorgetroya80/donations-api/compare/v1.4.0...v1.4.1) (2026-05-21)


### Bug Fixes

* sync pnpm lockfile after removing @hey-api/client-fetch peer dep ([#21](https://github.com/jorgetroya80/donations-api/issues/21)) ([d0c8133](https://github.com/jorgetroya80/donations-api/commit/d0c813335dd6c6fe76b16c1730c6518febe1b501))

## [1.4.0](https://github.com/jorgetroya80/donations-api/compare/v1.3.0...v1.4.0) (2026-05-21)


### Features

* generate and publish typed API client npm package on release ([#19](https://github.com/jorgetroya80/donations-api/issues/19)) ([5140514](https://github.com/jorgetroya80/donations-api/commit/51405146245015c5bc23fb07620d97e7b94307fc))

## [1.3.0](https://github.com/jorgetroya80/donations-api/compare/v1.2.0...v1.3.0) (2026-05-19)


### Features

* update GH actions versions ([#16](https://github.com/jorgetroya80/donations-api/issues/16)) ([3a30614](https://github.com/jorgetroya80/donations-api/commit/3a306149db24784b267d15c27fb48b5880515bba))

## [1.2.0](https://github.com/jorgetroya80/donations-api/compare/v1.1.1...v1.2.0) (2026-05-13)


### Features

* **config:** externalize DB credentials and CORS origins ([#13](https://github.com/jorgetroya80/donations-api/issues/13)) ([9ab5c50](https://github.com/jorgetroya80/donations-api/commit/9ab5c5006b83c864cd1847e70e4e0ea692b03037))

## [1.1.1](https://github.com/jorgetroya80/donations-api/compare/v1.1.0...v1.1.1) (2026-05-08)


### Bug Fixes

* **release:** scope permissions per-job, add workflow_dispatch and  fix ARM64 publish ([#10](https://github.com/jorgetroya80/donations-api/issues/10)) ([e89f7c3](https://github.com/jorgetroya80/donations-api/commit/e89f7c3790fb60aa53bbb4fe2995cbd5f15195b7))

## [1.1.0](https://github.com/jorgetroya80/donations-api/compare/v1.0.0...v1.1.0) (2026-04-17)


### Features

* **docker:** containerize API for frontend development ([e32fdc6](https://github.com/jorgetroya80/donations-api/commit/e32fdc61dc7bfb588bb9f975e5a3d75ef32130b4))
* **openapi:** add profile-based Swagger UI access and spec tests ([f6661aa](https://github.com/jorgetroya80/donations-api/commit/f6661aa04d3a0c3b740f229157cb68b3a0b570dc))
