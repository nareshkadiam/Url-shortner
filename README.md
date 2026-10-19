# URL Shortener

Spring Boot 3 (Java 17+) URL shortener with **Supabase PostgreSQL** for persistence, **Redis** for fast redirect
lookups, and **Swagger UI** for exploring the API.

## Features

| Feature | Details |
|---|---|
| Shorten | `POST /api/v1/urls` - random base62 code (7 chars, collision-checked and retried), optional custom alias and expiry |
| Redirect | `GET /{shortCode}` - Redis first, Supabase on a miss, async click increment, `302` (configurable `301`) |
| Stats | `GET /api/v1/urls/{shortCode}/stats` - click count, created/expiry timestamps, metadata |
| Cleanup | `@Scheduled` job (hourly by default) purges expired rows from Supabase and keys from Redis in batches |
| Errors | Structured JSON for every error (400 / 404 / 409 / 410 / 500 / 503) |
| Docs | Swagger UI at `/swagger-ui.html`, OpenAPI JSON at `/v3/api-docs` |
| Health | `/actuator/health` |

## Architecture

```
Controller  ->  Service  ->  Repository (Spring Data JPA) -> Supabase PostgreSQL
                  |
                  +-> UrlCacheService (Redis)     redirect hot path
                  +-> ClickCounterService (@Async) atomic "click_count = click_count + 1"
ExpiredUrlCleanupJob (@Scheduled) -> Repository + UrlCacheService
```

Design notes:

- **Redirect hot path** is a single Redis `GET`. On a miss it loads from Postgres and repopulates the cache. Cache TTL is
  `min(app.cache-ttl, time until the link expires)`, so a cached entry can never outlive its link.
- **Redis is optional at runtime.** Any Redis failure is logged and the request falls back to the database.
- **Click counting never blocks a redirect.** It runs on a bounded executor; if saturated, increments are dropped and logged.
- **Collision resistance:** codes come from `SecureRandom`; an `existsBy` pre-check plus the `UNIQUE` constraint
  (catching the constraint violation) handle races. Custom aliases that lose a race return `409`.
- **302 by default.** Browsers cache `301` permanently, which would bypass click counting and expiry. Set
  `APP_REDIRECT_STATUS=301` if you prefer permanent redirects.
- Reserved aliases (`api`, `swagger-ui`, `v3`, `actuator`, `error`, `health`, `docs`) are rejected.

## Prerequisites

- JDK 17+
- Maven 3.9+ (or let your IDE import `pom.xml`)
- A Supabase project
- Redis (Docker, or a hosted service such as Upstash)

## 1. Configure Supabase

1. In Supabase open **Project Settings -> Database -> Connection string** and select **JDBC**.
2. Pick a pooled connection (Supavisor):
   - **Session mode, port 5432** - recommended for a long-running Spring app (full prepared-statement support).
   - **Transaction mode, port 6543** - for serverless/many instances. Append `prepareThreshold=0` to the URL because
     transaction pooling does not support server-side prepared statements.
3. Set the values as environment variables (see `.env.example`):

```bash
# Session pooler
SPRING_DATASOURCE_URL="jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require"
# Transaction pooler
# SPRING_DATASOURCE_URL="jdbc:postgresql://aws-0-<region>.pooler.supabase.com:6543/postgres?sslmode=require&prepareThreshold=0"

SPRING_DATASOURCE_USERNAME="postgres.<project-ref>"     # note the project-ref suffix on pooler connections
SPRING_DATASOURCE_PASSWORD="<your database password>"
```

These map onto `spring.datasource.url`, `spring.datasource.username` and `spring.datasource.password` in
`src/main/resources/application.properties`. You may also edit that file directly, but **do not commit real passwords**.

4. **Create the table.** Either:
   - let Hibernate create it on first start (default `JPA_DDL_AUTO=update`), or
   - paste `src/main/resources/db/schema.sql` into the Supabase **SQL editor**, then run the app with
     `JPA_DDL_AUTO=validate` (recommended for production).

   The schema enables Row Level Security so the table is not exposed via Supabase's REST API; the app connects as the
   `postgres` role over JDBC, which bypasses RLS.

## 2. Run Redis

Local Docker (included `docker-compose.yml`):

```bash
docker compose up -d redis
```

The default `REDIS_URL=redis://localhost:6379` then works as is. For a hosted instance (e.g. Upstash) use its TLS URL:

```bash
REDIS_URL="rediss://default:<password>@<host>.upstash.io:6379"
```

## 3. Start the app

```bash
# Linux / macOS
export SPRING_DATASOURCE_URL=... SPRING_DATASOURCE_USERNAME=... SPRING_DATASOURCE_PASSWORD=...
mvn spring-boot:run
```

```powershell
# Windows PowerShell
$env:SPRING_DATASOURCE_URL="..."; $env:SPRING_DATASOURCE_USERNAME="..."; $env:SPRING_DATASOURCE_PASSWORD="..."
mvn spring-boot:run
```

Or build and run the jar / Docker image:

```bash
mvn clean package
java -jar target/url-shortener-1.0.0.jar

docker build -t url-shortener .
docker run -p 8080:8080 --env-file .env url-shortener
```

Open <http://localhost:8080/swagger-ui.html>.

## Configuration reference

| Environment variable | Property | Default | Description |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` | `jdbc:postgresql://localhost:5432/postgres` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` | `postgres` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | `spring.datasource.password` | _(empty)_ | DB password |
| `DB_POOL_SIZE` | `spring.datasource.hikari.maximum-pool-size` | `10` | Keep within your Supabase pooler limits |
| `JPA_DDL_AUTO` | `spring.jpa.hibernate.ddl-auto` | `update` | Use `validate` once the schema exists |
| `REDIS_URL` | `spring.data.redis.url` | `redis://localhost:6379` | `redis://` or `rediss://` |
| `APP_BASE_URL` | `app.base-url` | `http://localhost:8080` | Used to build `shortUrl` in responses |
| `APP_REDIRECT_STATUS` | `app.redirect-status` | `302` | `301` or `302` |
| `APP_CODE_LENGTH` | `app.code-length` | `7` | Generated code length |
| `APP_CACHE_TTL` | `app.cache-ttl` | `24h` | Max Redis TTL per entry |
| `APP_CLEANUP_CRON` | `app.cleanup-cron` | `0 0 * * * *` | Spring cron (hourly) |
| `APP_CLEANUP_BATCH_SIZE` | `app.cleanup-batch-size` | `500` | Rows deleted per batch |
| `PORT` | `server.port` | `8080` | HTTP port |

## API examples

Create:

```bash
curl -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com/a/very/long/path","customAlias":"my-link","expiresAt":"2030-01-01T00:00:00Z"}'
```

```json
{
  "shortCode": "my-link",
  "shortUrl": "http://localhost:8080/my-link",
  "originalUrl": "https://example.com/a/very/long/path",
  "createdAt": "2026-10-19T13:40:00.123Z",
  "expiresAt": "2030-01-01T00:00:00Z"
}
```

`customAlias` and `expiresAt` are optional.

Redirect / stats:

```bash
curl -i http://localhost:8080/my-link                    # 302 with Location header
curl http://localhost:8080/api/v1/urls/my-link/stats
```

Error format:

```json
{
  "timestamp": "2026-10-19T13:41:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/urls",
  "fieldErrors": [
    { "field": "url", "message": "must be a valid http or https URL (max 2048 characters)" }
  ]
}
```

| Status | When |
|---|---|
| 400 | Invalid URL / alias / expiry, malformed JSON, reserved alias |
| 404 | Unknown short code |
| 409 | Custom alias already in use |
| 410 | Link has expired |
| 503 | Could not generate a unique code after 5 attempts |

## Tests

```bash
mvn test
```

JUnit 5 + Mockito unit tests for the service layer (create, alias conflicts, cache hit/miss/expiry, stats), Redis cache
behaviour (TTL capping, outage tolerance), the cleanup job, code generator and URL validator, plus `@WebMvcTest`
controller tests covering validation and the JSON error responses. No database or Redis is needed to run them.

## Production notes

- **Multiple instances:** the cleanup job runs on every instance. It is idempotent, but add
  [ShedLock](https://github.com/lukas-krecan/ShedLock) if you want it to run on one node at a time.
- **Abuse protection:** no authentication or rate limiting is included. Put the create endpoint behind an API gateway or
  add Spring Security / a rate limiter before exposing it publicly, since open shorteners are commonly abused.
- **Click counts** are a best-effort counter (dropped under executor saturation or DB failure) - fine for analytics, not billing.
- Expired aliases remain reserved until the cleanup job removes the row.
