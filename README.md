# stinger-check-api

Backend for [Stinger Check](https://stingercheck.com). Lets users search movies (via TMDB), vote on whether a movie has a stinger (a post-credit scene), and see the aggregated verdict.

## Tech stack

- Java 21, Spring Boot 4
- PostgreSQL (data) + Flyway (migrations)
- Redis (caching + session store)
- Spring Security with Google OAuth2 login
- TMDB API (movie search/metadata)

## Prerequisites

- JDK 21
- Docker (for Postgres and Redis)
- A [TMDB API key](https://www.themoviedb.org/settings/api)
- A Google OAuth2 client ID/secret (only needed if you're testing login; the app runs fine without it otherwise)

## Running locally

1. **Start dependencies** (Postgres on `5432`, Redis on `6379`):

   ```bash
   docker-compose up -d
   ```

2. **Configure local secrets.** `application-local.properties` is git-ignored, so create it yourself under `src/main/resources/`:

   ```properties
   tmdb.api.key=YOUR_TMDB_API_KEY

   spring.security.oauth2.client.registration.google.client-id=YOUR_GOOGLE_CLIENT_ID
   spring.security.oauth2.client.registration.google.client-secret=YOUR_GOOGLE_CLIENT_SECRET
   ```

   Without this file the app still starts, using the `changeme` placeholders from `application.properties` — movie search/details and OAuth login just won't work until real credentials are supplied.

3. **Run the app** with the `local` profile active:

   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
   ```

   The API is served at `http://localhost:8080`. Flyway migrations run automatically on startup.

Alternatively, skip step 2 and pass credentials as environment variables against the default profile:

```bash
TMDB_API_KEY=xxx GOOGLE_CLIENT_ID=xxx GOOGLE_CLIENT_SECRET=xxx ./mvnw spring-boot:run
```

## Configuration reference

| Property / env var | Default | Purpose |
|---|---|---|
| `spring.datasource.url` / `.username` / `.password` | `jdbc:postgresql://localhost:5432/stingercheck` / `postgres` / `secret` | Postgres connection, matches `docker-compose.yml` |
| `spring.data.redis.host` / `.port` | `localhost` / `6379` | Redis connection |
| `TMDB_API_KEY` | `changeme` | TMDB API key for movie search/details |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | `changeme` | Google OAuth2 login credentials |
| `app.frontend-url` | `http://localhost:5173` | Allowed CORS origin and OAuth2 redirect target |
| `app.vote-ip-retention-days` | `30` | Days a vote keeps its IP address (used for rate limiting) before an hourly job clears it |

The frontend is expected to run separately at `http://localhost:5173`.

## API overview

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/movies/search?q=` | public | Search movies via TMDB, annotated with current vote-based verdict |
| `GET` | `/api/movies/now-playing` | public | Movies in cinemas now, with verdicts |
| `GET` | `/api/movies/{tmdbId}` | public | Movie details, vote summary and verdict; 404 if TMDB doesn't know the id |
| `POST` | `/api/movies/{tmdbId}/votes` | public | Cast or change a vote (`{"hasStinger": true/false}`); identified by session (logged-in) or `X-Anonymous-Token` header. Returns the new tallies and verdict |
| `DELETE` | `/api/movies/{tmdbId}/votes` | public | Retract a vote |
| `GET` | `/api/auth/me` | public | Current authenticated user, or `204 No Content` when signed out |
| `POST` | `/api/auth/me/claim-votes` | signed in | Move the anonymous votes of the `X-Anonymous-Token` browser to the account (the account's own vote wins) |
| `POST` | `/api/auth/logout` | public | Log out |
| `DELETE` | `/api/auth/me` | signed in | Delete the current account; its votes stay in the tallies but are anonymized |
| `GET` | `/api/oauth2/authorization/google?returnTo=/path` | public | Start Google OAuth2 login; after `/api/login/oauth2/code/google` the user returns to `returnTo` (a path on this site) |
| `GET` | `/sitemap.xml` | public | Sitemap for search engines (home, privacy, now-playing and voted movies) |
| `GET` | `/actuator/health` | public | Health check (liveness/readiness probes under `/actuator/health/{liveness,readiness}`) |

Anonymous voting is tracked via an `X-Anonymous-Token` request header (client-generated); authenticated users vote via their OAuth2 session instead.

Votes are rate-limited per IP address: 20 vote requests per hour (including requests for movies TMDB doesn't know, so they can't cause unlimited TMDB calls), and at most 2 new anonymous votes per movie per day (so one connection can't reach a verdict alone). Limits count requests in `vote_attempts`, so retracting a vote doesn't reset them: withdrawing and voting again counts as a new vote. A per-IP Postgres advisory lock makes concurrent requests from one connection take turns, so they can't slip past the limits together. Votes are only accepted for movies TMDB knows; TMDB 404s are remembered for an hour. API errors keep their status code (401 for unauthenticated `/api` calls, 404, 429, 502 when TMDB fails) instead of redirecting to the login page.

## Running tests

```bash
./mvnw test
```

There's no test-specific configuration or embedded/testcontainers setup yet, so the test suite boots the full Spring context against the same Postgres/Redis from `docker-compose.yml` — make sure those are running first (step 1 above).

## Docker

```bash
docker build -t ghcr.io/iamandresilva/stinger-check-api:local .
```

Pushes to `main` build and publish `ghcr.io/iamandresilva/stinger-check-api` (tagged with the commit SHA and `latest`) via `.github/workflows/docker.yml`.

## Deploying to Kubernetes

Manifests live in `k8s/` and are applied with Kustomize. They run the API, the UI ([stinger-check-ui](https://github.com/IamAndreSilva/stinger-check-ui)), Postgres and Redis in the `stinger-check` namespace, plus a nightly `pg_dump` CronJob.

| Path | Purpose |
|---|---|
| `k8s/base` | Workloads shared by every environment |
| `k8s/overlays/local` | Local cluster (minikube/kind), placeholder secrets, no ingress |
| `k8s/overlays/prod` | Ingress with TLS: `/api` goes to the API, everything else to the UI |
| `k8s/cluster` | One-off cluster setup for k3s: Let's Encrypt issuer and Traefik settings |

### Local cluster

```bash
minikube start
minikube image load ghcr.io/iamandresilva/stinger-check-api:local ghcr.io/iamandresilva/stinger-check-ui:local
kubectl apply -k k8s/overlays/local
kubectl -n stinger-check port-forward svc/api 8080:8080
kubectl -n stinger-check port-forward svc/ui 8081:80
```

### Production (k3s)

1. Set the public hostname in `k8s/overlays/prod` (currently `stingercheck.com`) and point its DNS `A` record at the server.
2. Install [cert-manager](https://cert-manager.io/docs/installation/), then `kubectl apply -f k8s/cluster/`.
3. Create the secret (never committed):

   ```bash
   kubectl create namespace stinger-check
   kubectl -n stinger-check create secret generic stinger-check-secrets \
     --from-literal=POSTGRES_PASSWORD=... \
     --from-literal=TMDB_API_KEY=... \
     --from-literal=GOOGLE_CLIENT_ID=... \
     --from-literal=GOOGLE_CLIENT_SECRET=...
   ```

   The images are private, so the cluster also needs a registry secret, created from a GitHub token that has only the `read:packages` scope:

   ```bash
   kubectl -n stinger-check create secret docker-registry ghcr-pull \
     --docker-server=ghcr.io --docker-username=<github-user> --docker-password=<token>
   ```

   Changing `POSTGRES_PASSWORD` later has no effect on an existing database volume; rotate it with `ALTER USER` as well.

4. `kubectl apply -k k8s/overlays/prod`
5. Add `https://<host>/api/login/oauth2/code/google` as an authorised redirect URI on the Google OAuth client.

The API trusts `X-Forwarded-*` headers in the cluster (`SERVER_FORWARD_HEADERS_STRATEGY=native`), so it must only be reachable through the ingress.
