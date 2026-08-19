# admin-client

Admin dashboard for the AI spam-filter project ("Warden"). A self-contained
Java SE app — embedded Tomcat, no external servlet container needed — for
moderating traffic on the shared Neon (Postgres) database: message/call
history, system logs, and the blocklist/whitelist.

Independent of `sms-client` — its own login, its own port — but reads and
writes the same Neon database.

## Tech stack

- Java 17
- Embedded [Tomcat](https://tomcat.apache.org/) (`tomcat-embed-core`) — the whole app is a single runnable jar, no WAR deploy
- Plain `HttpServlet`s serving server-rendered HTML (Java text blocks) with vanilla JS for the interactive bits — no frontend build step, no framework
- [Jackson](https://github.com/FasterXML/jackson) for the JSON `/api/*` endpoints
- PostgreSQL via plain JDBC, pooled with [HikariCP](https://github.com/brettwooldridge/HikariCP)
- [SLF4J](https://www.slf4j.org/) (simple binding) for logging
- JUnit 5 + H2 (in-memory, Postgres-compat mode) for repository tests
- Maven, packaged into a single runnable jar with the shade plugin

## Prerequisites

- JDK 17+
- Maven 3.8+
- A reachable Neon (Postgres) connection string for the shared schema (`users`, `messages`, `calls`, `logs`, `blocklist`, `whitelisted_senders`, `subscribers`)

## Configuration

Config is read from `src/main/resources/application.properties`, with each
key overridable by a `-Dkey=value` system property or an environment
variable (`server.port` → `SERVER_PORT`, `db.url` → `DB_URL`). System
property wins over env var, which wins over the properties file.

| Key | Env var | Default | Notes |
|---|---|---|---|
| `server.port` | `SERVER_PORT` | `8082` | HTTP port the embedded Tomcat listens on |
| `db.url` | `DB_URL` | _(empty)_ | Neon connection string, e.g. `postgresql://user:password@host/dbname?sslmode=require`. **Never commit a real value** — pass it at launch only |

If `db.url` is unset or invalid, the app still starts (so you can see the
login page and logs), but every database-backed page/API call will fail.

## Running

```sh
mvn package
java -Dserver.port=8082 -Ddb.url="postgresql://user:password@host/db?sslmode=require" -jar target/admin-client.jar
```

or with the environment variable instead of `-Ddb.url`:

```sh
export DB_URL='postgresql://user:password@host/db?sslmode=require'
mvn clean package
java -Dserver.port=8082 -Ddb.url="$DB_URL" -jar target/admin-client.jar
```

Then open `http://localhost:8082`.

## Access

Login is by email/password against the shared `users` table — there's no
separate admin account system. An account needs `role = ROLE_ADMIN` or
`ROLE_ESCALATION` to sign in here; `ROLE_SUPPORT` and unknown emails are
rejected. Passwords are stored the same way `sms-client` stores them (this
reads the same `password` column, not a copy).

## Pages

| Route | Purpose |
|---|---|
| `/login`, `/logout` | Admin sign-in / sign-out |
| `/` | Dashboard — KPI tiles, a 14-day spam/ham chart, a spam-rate ring, and a feed of recent system log entries |
| `/messages` | Every classified SMS across every subscriber, filterable by verdict/status/number |
| `/calls` | Every classified voice call across every subscriber, filterable by status/number |
| `/blacklist` | Numbers blocked outright, checked before classification runs — add/remove, with expiry support |
| `/whitelist` | Trusted senders that bypass spam classification — add/remove |
| `/logs` | System log entries, filterable by severity/query, with related-record lookups |

Each of those pages (other than the dashboard) is backed by a matching JSON
API under `/api/...` (`/api/messages`, `/api/calls`, `/api/blocklist` +
`/api/blocklist/details`, `/api/whitelist`, `/api/logs`) that the page's own
inline JS calls for search, pagination, and row mutations.

## Testing

```sh
mvn test
```

Repository tests spin up an in-memory H2 database in PostgreSQL-compat mode
per test, so they don't need a real Neon connection.

## Building a runnable jar

```sh
mvn package
```

Produces `target/admin-client.jar` — a single self-contained jar
(`java -jar target/admin-client.jar`), no external servlet container or
other runtime dependency needed beyond the JVM and network access to Neon.

## Security notes

- `db.url` carries a live database password — only ever pass it via
  `-Ddb.url=...`/`DB_URL` at launch, never commit it to
  `application.properties` or any tracked file.
- Login passwords are stored in plaintext in the shared `users` table (this
  app doesn't hash them itself, matching `sms-client`'s existing scheme) —
  don't reuse this deployment's credentials elsewhere.
