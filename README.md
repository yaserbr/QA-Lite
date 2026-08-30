# QA Lite

A Spring Boot web app for QA teams: sign in, pick an allowed environment and a predefined SQL command, connect them on the dashboard, and run the command.

## Tech Stack

Java 21 · Spring Boot 4.1 (MVC, Security, JDBC) · Thymeleaf · Oracle · Flyway · Maven Wrapper
Target databases supported: PostgreSQL, Oracle, MySQL, MongoDB Atlas SQL.

## Quick Start

1. **Database** — create a dedicated Oracle schema/user for the app (tables are created automatically by Flyway). See [docs/oracle-setup.md](docs/oracle-setup.md) for the one-time DBA setup script.
2. **Config** — copy `.env.example` to `.env` and fill in:
   ```properties
   QALITE_DB_URL=jdbc:oracle:thin:@//HOST:PORT/SERVICE_NAME
   QALITE_DB_USERNAME=USERNAME
   QALITE_DB_PASSWORD=PASSWORD
   QALITE_SECRET_KEY=<a long random value>   # encrypts target DB passwords
   ```
3. **Run**:
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
   Open `http://localhost:8080/QaLite/login`.

**Build:** `.\mvnw.cmd -DskipTests package` → `target/qa-lite-0.0.1-SNAPSHOT.war`
**Test:** `.\mvnw.cmd test`

## Local Development (No Oracle Server Available)

If you don't have access to an Oracle instance yet and just want to run the app
locally to develop or test, spin up a free, disposable Oracle Database in
Docker instead of the "real" one-time DBA setup:

```bash
docker run -d --name qalite-oracle \
  -p 1521:1521 \
  -e ORACLE_PASSWORD=change_me_sys_password \
  -e APP_USER=qalite \
  -e APP_USER_PASSWORD=change_me_app_password \
  -v qalite-oracle-data:/opt/oracle/oradata \
  --restart unless-stopped \
  gvenzl/oracle-free:latest
```

First startup takes a minute or two (creating the database). Watch for
`DATABASE IS READY TO USE!` in `docker logs -f qalite-oracle`. Once ready, set
`.env` to:

```properties
QALITE_DB_URL=jdbc:oracle:thin:@//localhost:1521/FREEPDB1
QALITE_DB_USERNAME=qalite
QALITE_DB_PASSWORD=change_me_app_password
QALITE_SECRET_KEY=<a long random value>
```

Then run as usual (`.\mvnw.cmd spring-boot:run`). This container is only for
local development — it has nothing to do with, and is not required for,
deploying to a real server (see below).

## Routes

| Route | Purpose |
| --- | --- |
| `GET /login` | Login page |
| `POST /register` | Create a `QA_USER` account |
| `GET /` | Dashboard (environments/SQL commands allowed for the user) |
| `GET /admin` | Admin page (`ADMIN` role only) |
| `POST /admin/environments` | Add an environment |
| `POST /admin/sql` | Add a SQL command |
| `POST /admin/users/{userId}/permissions` | Update a user's access |

All routes are prefixed with `/QaLite`.

## Deploy to a Server

The built file is fully self-contained — it already includes an embedded web server, so nothing else needs to be installed on the server except Java. Building it (`mvnw package`) needs internet access to download dependencies, so if the target server is offline (e.g. an internal database server with no internet access), **build the WAR on a machine that has internet access and copy only the built file over** — the server itself never needs internet or Maven.

**Requirements on the server:** Java 21+ only, and a dedicated Oracle schema/user for the app — see [docs/oracle-setup.md](docs/oracle-setup.md) for the one-time DBA setup script (it creates an isolated user inside the existing Oracle instance, it does not touch any existing data).

**What to copy to the server** (from a machine where you ran `mvnw package`):
- `target/qa-lite-0.0.1-SNAPSHOT.war`
- `.env.example` (rename to `.env` on the server and fill in the real values)

**Linux — on the server, after copying the two files into the same folder:**
```bash
mv .env.example .env
nano .env                       # fill in the real Oracle host/port/service name/user/password
                                 # and set QALITE_SECRET_KEY to a long random value
java -jar qa-lite-0.0.1-SNAPSHOT.war
```
To keep it running after you log out:
```bash
nohup java -jar qa-lite-0.0.1-SNAPSHOT.war > qa-lite.log 2>&1 &
```

**Windows Server — same idea:**
```powershell
ren .env.example .env
notepad .env                    # fill in the same values as above
java -jar qa-lite-0.0.1-SNAPSHOT.war
```

Once it's running, open `http://SERVER_HOST:8080/QaLite/login`. To change the port, set the `SERVER_PORT` environment variable before running.

If the server *does* have internet access, you can instead `git clone` the repo on it and run `./mvnw -DskipTests package` there — same result, just built in place.

## Database

Flyway migrations live in `src/main/resources/db/migration`:
- `V1__init_schema.sql` — schema
- `V2__seed_sample_data.sql` — sample data

Main tables: `users`, `environments`, `sql_definitions`, `user_allowed_env`, `user_allowed_sql`, `execution_history`.

Add new changes as a new versioned file (e.g. `V3__...sql`) — never edit an already-applied migration on a shared database.

## Target Database Types

There are two separate databases in play, and they are configured differently:

1. **The app's own storage database** — holds `users`, `environments`, `sql_definitions`, etc. This is a single Oracle database configured once via `QALITE_DB_URL` / `QALITE_DB_USERNAME` / `QALITE_DB_PASSWORD` in `.env` (see [Quick Start](#quick-start)). Nothing below changes this.
2. **Target environments** — the databases QA users actually run SQL against (e.g. "SIT", "UAT"). Each one is added independently by an admin from the `/admin` page ("Add Environment"), with its own JDBC URL, username, and password — no server restart or `.env` change needed.

Supported target `Database Type` values: **PostgreSQL**, **Oracle**, **MySQL**, **MongoDB Atlas SQL** (`com.mobily.qalite.targetdb.TargetDatabaseType`). To add a MySQL target environment:

1. Go to `/admin` → **Add Environment**.
2. Set **Database Type** to `MySQL`.
3. **JDBC URL**: `jdbc:mysql://host:3306/database` (add JDBC params like `?useSSL=true` directly in the URL if needed).
4. Fill in the DB username/password — the password is encrypted at rest (AES/GCM, `QALITE_SECRET_KEY`).
5. Click **Test** next to the new row under "Current Environments" to confirm QA Lite can reach it — this opens and closes a short-lived, isolated connection pool for that one environment only; it never touches the app's own storage database or any other environment's connections.

Each target connection is opened on demand with a small dedicated Hikari pool (`TargetDatabaseConnectionService`) and closed after use — there's no shared connection pool between environments, so adding MySQL cannot interfere with PostgreSQL/Oracle/MongoDB environments already configured. There's no JPA/Hibernate layer in this project (see [Project Structure](#project-structure)) — all target-database access is plain JDBC (`JdbcTemplate` over the SQL text the admin defines), so there's no ORM dialect to configure per database type; each `TargetDatabaseType` just carries its JDBC driver class name and URL prefix for validation.

## Security

- Passwords hashed with BCrypt; target DB passwords encrypted (AES/GCM) using `QALITE_SECRET_KEY`.
- Self-registration always creates `QA_USER`; admins have implicit full access.
- `/admin/**` requires `ROLE_ADMIN`; login/register are rate-limited per IP.
- Admin-defined SQL commands can be **any** SQL, including writes and DDL (`INSERT`/`UPDATE`/`DELETE`/`DROP`/...) — there is no read-only restriction. Admins are fully trusted (see above), but remember that any `QA_USER` granted access to a destructive command can run it against that command's environment, so grant destructive commands deliberately.
- Standard security headers (CSP, X-Frame-Options, HSTS on HTTPS, etc.).

## Project Structure

```text
src/main/java/com/mobily/qalite/
├── QaliteApplication.java
├── config/SecurityConfig.java
├── controller/           # PageController, AdminController, RegistrationController
├── admin/AdminService.java
├── dashboard/DashboardService.java
├── targetdb/              # TargetDatabaseType, TargetDatabaseConnectionService
└── security/               # DatabaseUserDetailsService, RegistrationService,
                             # SecretCipherService, rate limit & headers filters
src/main/resources/
├── application.yaml
├── db/migration/
├── templates/               # login, dashboard, admin
└── static/
```

## Status / Not Yet Implemented

Dashboard SQL execution currently renders sample frontend output — the real execution service (run the selected SQL against the selected target environment, enforce access checks, log to `execution_history`) is not built yet.
