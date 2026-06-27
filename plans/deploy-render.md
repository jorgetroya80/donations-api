# Deploy Donations API to Render (Docker + GitHub)

## Context

The API (Spring Boot 4 JVM, Dockerized) already uses Neon as its remote database via
environment variables. The user wants to deploy it on Render by connecting the GitHub
repo, building with Docker. Goal: every push to `main` redeploys the API, connected to
Neon, reachable over public HTTPS on Render's **free** tier.

Two blockers found in the current code:
1. **Fixed port** — `application.yaml:20` uses `port: 8081`, but Render injects a dynamic
   `$PORT`. If the app does not listen on `$PORT`, Render marks the deploy as down.
2. **Memory** — free tier = 512MB. An idle Spring Boot 4 JVM sits at ~300-400MB. Without
   a RAM flag the container can be OOM-killed under load.

User decisions: **prod** profile (Secure cookie over HTTPS; Swagger disabled) +
configuration as a **render.yaml** blueprint versioned in the repo. Plan reviewed by the
devops-engineer agent; corrections incorporated below.

## Repo changes

### 1. Dynamic port — `src/main/resources/application.yaml`
Line 20: `port: 8081` → `port: ${PORT:8081}`
Keeps 8081 locally (default), uses Render's `$PORT` in the cloud.

### 2. RAM limit — `Dockerfile`
Line 15 ENTRYPOINT:
```dockerfile
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```
Caps the JVM heap at 75% of 512MB → leaves room for metaspace/threads, avoids OOM.
(If OOM appears under real load, drop to 70.0.)

### 3. Forward headers (Secure cookie behind proxy) — `application.yaml`
Render terminates TLS at its edge and forwards HTTP to the container. Without this, Spring
sees the request as plain HTTP → the prod profile's `Secure` cookie misbehaves. Add to the
`prod` profile block (lines 41-49, next to `cookie.secure: true`):
```yaml
server:
  forward-headers-strategy: framework
```
Confirmed by the devops review; currently NOT SET in the yaml.

### 4. Blueprint — `render.yaml` (new, repo root)
```yaml
services:
  - type: web
    name: donations-api
    runtime: docker
    dockerfilePath: ./Dockerfile
    plan: free
    region: frankfurt              # co-locate with Neon eu-central-1 (~100ms less/round-trip)
    healthCheckPath: /actuator/health
    autoDeploy: true
    envVars:
      - key: SPRING_PROFILES_ACTIVE
        value: prod
      - key: SPRING_DATASOURCE_URL
        sync: false        # secret -> filled in dashboard
      - key: SPRING_DATASOURCE_USERNAME
        sync: false
      - key: SPRING_DATASOURCE_PASSWORD
        sync: false
```
- `region: frankfurt` → Render free defaults to Oregon (US); without this every query
  crosses the Atlantic to Neon `eu-central-1`. Highest-impact fix from the review.
- `healthCheckPath` → `/actuator/health` is already `permitAll` (`SecurityConfig.kt:37`),
  actuator already on the classpath (`build.gradle:29`).
- `sync: false` = the key exists but the value is NOT committed; set it in the dashboard.
- `autoDeploy: true` = push to `main` redeploys.

### 5. Update `docs/ARCHITECTURE.md` to reflect the latest changes
The doc still describes a local Docker-only topology and a stale migration list. Update:
- **Header (line 3)**: bump version (1.3.0 → next, e.g. 1.4.0).
- **§1 Overview ASCII + §2 Network Architecture**: add the deployed topology — API runs
  on **Render** (`frankfurt`, Docker, dynamic `$PORT`, TLS terminated at Render edge with
  `forward-headers-strategy: framework`), database is **Neon managed Postgres**
  (`eu-central-1`, JDBC over TLS `sslmode=require`, `-pooler` endpoint), not the local
  Docker container. Keep local Docker as the *dev* topology, add Render+Neon as *prod*.
- **§2 Environment Variables (lines 85-93)**: update the example `SPRING_DATASOURCE_URL`
  to the Neon pooler host; note `SPRING_PROFILES_ACTIVE=prod` is set on Render and that
  CORS stays disabled (same-origin frontend proxy).
- **Migrations — stale**: §3 component diagram says `Flyway V1–V6`; §5 + §9 list only
  through `V7`. Add **`V8__add_donor_search_trgm_index.sql`** (pg_trgm GIN indexes on
  `donors.full_name` / `national_id`) and fix the `V1–V6` label to `V1–V8`.
- **§5 Indexes table**: add the two trgm GIN indexes from V8.
- (Optional) **§2**: add the frontend → nginx `/api` same-origin proxy → API edge, since
  that is the deployed request path (cross-reference the appendix).

## Render dashboard steps (no code)

1. **New → Blueprint** → connect the GitHub repo → Render detects `render.yaml`.
2. After the service is created, in **Environment** fill the 3 `sync:false` secrets with
   the Neon values from your `.env`:
   - `SPRING_DATASOURCE_URL` = `jdbc:postgresql://ep-raspy-paper-asfh0qb1-pooler.c-4.eu-central-1.aws.neon.tech/neondb?sslmode=require`
   - `SPRING_DATASOURCE_USERNAME` = `neondb_owner`
   - `SPRING_DATASOURCE_PASSWORD` = (the password)
3. Deploy. Render builds the Dockerfile and starts the app.

## Caveats

- **Flyway over the `-pooler` host (PgBouncer, transaction-mode)**: Flyway uses
  session-level advisory locks for its migration lock; a transaction-mode pooler can fail
  on the first deploy. Mitigation if the deploy hangs on migrations: point
  `SPRING_DATASOURCE_URL` at Neon's **direct** host (no `-pooler`) just so V1–V8 run; the
  app can stay on the pooler. (Since you already applied V1–V8 when connecting locally,
  Flyway likely re-runs nothing and this risk won't materialize.)
- **Cold start**: free tier sleeps after ~15min idle; first request after sleep takes
  ~30-60s (JVM boot + possible Neon cold start). Acceptable for testing.
- **CORS**: the `prod` profile does NOT enable CORS (`app.cors.enabled` is only true in
  `dev`). If you later connect a frontend on a different domain you'd need to enable CORS
  in prod and add `APP_CORS_ALLOWED_ORIGINS`. Out of scope now.
- **Swagger**: prod disables springdoc → no `/swagger-ui`. Test the API over plain HTTP
  (curl/Postman) or `/actuator/health`.
- **Flyway on first boot** runs V1–V8 on Neon. If already applied (when connecting
  locally), Flyway sees them applied and does not repeat. The test seed is NOT a migration
  and does not run on deploy.

## Verification

1. Push to `main` → Render → logs show startup with no `Connection refused` and
   `Tomcat started on port <PORT>`.
2. `curl https://donations-api.onrender.com/actuator/health` → `{"status":"UP"}`.
3. Hit a real endpoint (e.g. `GET /api/v1/donors`) with auth → returns data from Neon
   (the seed rows if you loaded them).

---

# APPENDIX (informational) — Frontend deploy on Render

> To execute in **another session**. Repo: `jorgetroya80/donations-frontend`
> (React 19 + Vite, session-cookie auth, served by nginx in Docker).

## Architectural constraint (why Docker Web Service, not Static Site)
The frontend calls the API under **same-origin `/api/`** (nginx proxy → API). This must
be preserved because:
- The CSP is strict: `connect-src 'self'` (nginx.conf) → the browser **blocks**
  cross-origin calls to the API.
- The session cookie is `SameSite=Lax` + `Secure` → it does **not** travel to a different
  domain.
- The API in the `prod` profile has **CORS disabled**.
Conclusion: front-nginx proxies `/api/` to the API → the browser only ever sees one
origin → cookies, CSP and CORS are all satisfied **without touching the API**. That is why
we reuse the existing `Dockerfile` + `nginx.conf` (designed exactly for this).

## Changes in the `donations-frontend` repo

### 1. `nginx.conf` — retarget the upstream to the public Render API
The current `proxy_pass http://api:8081;` points at the Docker-compose service `api`,
which does NOT exist on Render. Change it to the API's public Render URL (HTTPS upstream):
```nginx
location /api/ {
    resolver 8.8.8.8 valid=300s;                       # DNS to resolve the host
    set $api_upstream https://donations-api.onrender.com;
    proxy_pass $api_upstream$request_uri;              # variable => must preserve full URI
    proxy_ssl_server_name on;                          # SNI for upstream TLS
    proxy_set_header Host donations-api.onrender.com;  # correct routing on Render
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```
Notes:
- Using a **variable** in `proxy_pass` means nginx does NOT append the URI automatically →
  you must add `$request_uri` explicitly (includes the original `/api/...`).
- `proxy_ssl_server_name on` + `resolver` are required to speak HTTPS to an external host
  from nginx.
- The API sets the session cookie with no `Domain` attribute (host-only). Proxied
  server-side, the browser only sees the front's domain → the cookie is scoped to the
  front. Same-origin OK. (If login fails, add `proxy_cookie_domain
  donations-api.onrender.com $host;`.)

### 2. nginx listening port — `nginx.conf` / Dockerfile
`listen 80;` is fixed. Render injects `$PORT`. Render (Docker) **auto-detects** the open
port, so `listen 80` usually works as-is. If Render fails to detect it, templatize with an
entrypoint that runs `envsubst` of `$PORT` over `listen`, or expose 80 explicitly. Verify
on the first deploy before adding complexity.

### 3. Blueprint — `render.yaml` (new, in the frontend repo)
```yaml
services:
  - type: web
    name: donations-frontend
    runtime: docker
    dockerfilePath: ./Dockerfile
    plan: free
    region: frankfurt          # same region as the API for low proxy latency
    autoDeploy: true
    healthCheckPath: /          # nginx serves index.html at /
```
No runtime env vars: the front calls `/api` relative, it needs no API URL at build time.
(`.env.example` only has `DONATIONS_API_VERSION`, the build-time API-client version, not
the backend location.)

## Frontend verification
1. Deploy → open `https://donations-frontend.onrender.com` → the SPA loads.
2. Log in → DevTools → Network: the `/api/v1/...` request is **same-origin** (front's
   domain), returns 200, and the session cookie is set (Application → Cookies).
3. No CSP or CORS errors in the console. SPA routing does not 404 (the `try_files ...
   /index.html` fallback is already present).

## Deploy order
API **first** (the front needs it live for the proxy). Then the front. Once the real front
domain exists, recheck whether the API needs anything else (it should not: the proxy keeps
everything same-origin, so the API requires no CORS or changes).
