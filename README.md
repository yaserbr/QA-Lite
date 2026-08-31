# QA Lite

A Spring Boot web app for QA teams: sign in, pick an allowed environment and a predefined SQL command, connect them on the dashboard, and run the command.

This README is the single source of truth for the project — setup, configuration, running locally, deploying, and administration are all covered below.

## Table of Contents

- [Tech Stack](#tech-stack)
- [Requirements](#requirements)
- [Quick Start](#quick-start)
- [Local Development Without An Oracle Server](#local-development-without-an-oracle-server)
- [Configuration Reference (.env)](#configuration-reference-env)
- [Maven Commands](#maven-commands)
- [One-Time Oracle Setup (App's Own Database)](#one-time-oracle-setup-apps-own-database)
- [Target Database Types](#target-database-types)
  - [Adding a PostgreSQL Target Environment](#adding-a-postgresql-target-environment)
  - [Adding a MySQL Target Environment](#adding-a-mysql-target-environment)
  - [Adding an Oracle Target Environment](#adding-an-oracle-target-environment)
  - [Adding a MongoDB Atlas SQL Target Environment](#adding-a-mongodb-atlas-sql-target-environment)
- [Routes](#routes)
- [Deploy to a Server](#deploy-to-a-server)
- [Enabling HTTPS](#enabling-https)
- [Database Migrations](#database-migrations)
- [Security](#security)
- [Project Structure](#project-structure)
- [Status](#status)

## Tech Stack

Java 21 · Spring Boot 4.1 (MVC, Security, JDBC) · Thymeleaf · Oracle · Flyway · Maven Wrapper

Target databases supported (databases QA users run SQL against): PostgreSQL, Oracle, MySQL, MongoDB Atlas SQL.

## Requirements

To run QA Lite from scratch you need:

- **Java 21 or newer** (JDK, not just a JRE, if you plan to build from source). Check with `java -version`.
- **Maven** — not required to install separately; this project ships the Maven Wrapper (`mvnw` / `mvnw.cmd`), which downloads the correct Maven version automatically on first use.
- **Internet access** the first time you build — Maven needs to download dependencies (Spring Boot, JDBC drivers, etc.). Once built, the resulting `.war` file is fully self-contained and needs no internet access to run.
- **An Oracle database** for the app's own storage (users, environments, SQL definitions, permissions, execution history). This can be:
  - an existing Oracle instance your DBA gives you a dedicated schema on (see [One-Time Oracle Setup](#one-time-oracle-setup-apps-own-database)), or
  - a free, disposable Oracle container for local development only (see [Local Development Without An Oracle Server](#local-development-without-an-oracle-server)).
- **Target environment databases are optional at setup time** — QA Lite runs with zero target environments configured; admins add them later from the web UI (PostgreSQL, Oracle, MySQL, or MongoDB Atlas SQL — see [Target Database Types](#target-database-types)).

Nothing else needs to be installed. There is no separate application server to configure — Spring Boot embeds Tomcat.

## Quick Start

1. **Database** — get access to an Oracle schema for the app's own storage. See [One-Time Oracle Setup](#one-time-oracle-setup-apps-own-database) below (tables are created automatically by Flyway — you never write schema SQL by hand).
2. **Config** — copy `.env.example` to `.env` and fill in real values:
   ```properties
   QALITE_DB_URL=jdbc:oracle:thin:@//HOST:PORT/SERVICE_NAME
   QALITE_DB_USERNAME=USERNAME
   QALITE_DB_PASSWORD=PASSWORD
   QALITE_SECRET_KEY=<a long random value>   # encrypts target DB passwords
   ```
   See [Configuration Reference](#configuration-reference-env) for what every value means.
3. **Run**:
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
   Open `http://localhost:8080/QaLite/login` in a browser.
4. **Create your first account** — click register on the login page. The first account is a normal `QA_USER` with no access yet; to make it an admin (or to grant access to environments/SQL commands), update the `users` table role directly the first time, or have someone with existing `ADMIN` access grant it from `/QaLite/admin`.

## Local Development Without An Oracle Server

If you don't have access to an Oracle instance yet and just want to run the app locally to develop or test, spin up a free, disposable Oracle Database in Docker instead of the "real" one-time DBA setup:

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

First startup takes a minute or two (creating the database). Watch for `DATABASE IS READY TO USE!` in `docker logs -f qalite-oracle`. Once ready, set `.env` to:

```properties
QALITE_DB_URL=jdbc:oracle:thin:@//localhost:1521/FREEPDB1
QALITE_DB_USERNAME=qalite
QALITE_DB_PASSWORD=change_me_app_password
QALITE_SECRET_KEY=<a long random value>
```

Then run as usual (`.\mvnw.cmd spring-boot:run`). This container is only for local development — it has nothing to do with, and is not required for, deploying to a real server (see [Deploy to a Server](#deploy-to-a-server)).

## Configuration Reference (.env)

All configuration lives in a `.env` file in the project root (copy `.env.example` to start). It is loaded automatically by Spring Boot (`spring.config.import` in `application.yaml`) and is git-ignored — never commit real credentials.

| Variable | Required | Meaning |
| --- | --- | --- |
| `QALITE_DB_URL` | Yes | JDBC URL of the app's own Oracle database, e.g. `jdbc:oracle:thin:@//HOST:PORT/SERVICE_NAME`. |
| `QALITE_DB_USERNAME` | Yes | Username for that Oracle schema. |
| `QALITE_DB_PASSWORD` | Yes | Password for that Oracle schema. |
| `QALITE_DB_POOL_SIZE` | No (default `10`) | Max size of the Hikari connection pool used for the app's own database. |
| `QALITE_SECRET_KEY` | Yes | A long random value used to encrypt (AES/GCM) target-environment database passwords at rest. Generate one with e.g. `openssl rand -base64 32`. Changing this after environments have been added makes their stored passwords unreadable — treat it like a real secret and back it up. |
| `SERVER_PORT` | No (default `8080`) | Port the embedded web server listens on. |
| `QALITE_SSL_ENABLED` | No (default `false`) | Set to `true` to serve HTTPS instead of plain HTTP — see [Enabling HTTPS](#enabling-https). When enabled, `SERVER_PORT` becomes the HTTPS port and there is no separate HTTP port. |
| `QALITE_SSL_KEYSTORE` | Only if SSL enabled | Path to the certificate keystore, as a Spring resource location, e.g. `file:/path/to/qalite-keystore.p12`. |
| `QALITE_SSL_KEYSTORE_PASSWORD` | Only if SSL enabled | Password protecting the keystore file. |
| `QALITE_SSL_KEYSTORE_TYPE` | No (default `PKCS12`) | Keystore format — `PKCS12` for a `keytool`-generated or most CA-issued keystores; `JKS` for an older Java keystore. |
| `QALITE_SSL_KEY_ALIAS` | No (default `qalite`) | Alias of the key/certificate entry inside the keystore (the `-alias` value used when generating it). |

Other defaults (context path `/QaLite`, session timeout `30m`, logging levels) live in `src/main/resources/application.yaml` and normally don't need to change.

## Maven Commands

Run these from the project root. On Windows use `.\mvnw.cmd`; on Linux/macOS use `./mvnw`.

| Command | Purpose |
| --- | --- |
| `.\mvnw.cmd spring-boot:run` | Run the app locally with hot-reload (devtools), reading `.env`. |
| `.\mvnw.cmd test` | Run the test suite. |
| `.\mvnw.cmd -DskipTests package` | Build the deployable artifact at `target/qa-lite-0.0.1-SNAPSHOT.war`, skipping tests (fast build for deployment). |
| `.\mvnw.cmd package` | Build the artifact and run tests first. |
| `.\mvnw.cmd clean` | Remove the `target/` build output. |

## One-Time Oracle Setup (App's Own Database)

QA Lite stores its own data (users, environments, SQL definitions, permissions, execution history) in an Oracle schema. It does **not** need a brand new database — it just needs one dedicated, empty user/schema inside an Oracle instance. Flyway creates all the tables inside that schema automatically the first time the app starts; you never run schema SQL by hand.

**1. Create a dedicated schema (one-time DBA step).** Ask whoever has DBA access on the Oracle instance to run this once. It creates a new user with no access to any other schema's data:

```sql
CREATE USER qalite IDENTIFIED BY "choose-a-strong-password-here";
GRANT CONNECT, RESOURCE TO qalite;
GRANT UNLIMITED TABLESPACE TO qalite;
```

This does not touch, move, or expose any existing data on the server. The new `qalite` user starts completely empty and cannot see other schemas' tables unless explicitly granted.

To remove it later, if ever needed:
```sql
DROP USER qalite CASCADE;
```

**2. Collect the connection details** from whoever ran the SQL above:
- **Host** and **Port** of the Oracle listener (`localhost`/`1521` if the app runs on the same server as Oracle)
- **Service name** (or SID) of the existing database/PDB the user was created in
- The **username** (`qalite`) and the **password** chosen above

**3. Configure the application** — edit `.env` (copy from `.env.example`):

```properties
QALITE_DB_URL=jdbc:oracle:thin:@//HOST:PORT/SERVICE_NAME
QALITE_DB_USERNAME=qalite
QALITE_DB_PASSWORD=choose-a-strong-password-here
QALITE_SECRET_KEY=<a separate long random value, encrypts target DB passwords>
```

**4. Run** — see [Quick Start](#quick-start) or [Deploy to a Server](#deploy-to-a-server). Flyway creates every table from scratch on first startup. The app's context path is `/QaLite`.

## Target Database Types

There are two separate kinds of database in play, configured differently — don't confuse them:

1. **The app's own storage database** — holds `users`, `environments`, `sql_definitions`, etc. This is a single Oracle database configured once via `QALITE_DB_URL` / `QALITE_DB_USERNAME` / `QALITE_DB_PASSWORD` in `.env` (see [One-Time Oracle Setup](#one-time-oracle-setup-apps-own-database)). Nothing in this section changes that.
2. **Target environments** — the databases QA users actually run SQL against (e.g. "SIT", "UAT"). Each one is added independently by an admin from the `/QaLite/admin` page ("Add Environment"), with its own JDBC URL, username, and password — no server restart or `.env` change needed.

Supported target `Database Type` values: **PostgreSQL**, **Oracle**, **MySQL**, **MongoDB Atlas SQL** (`com.mobily.qalite.targetdb.TargetDatabaseType`).

Each target connection is opened on demand with a small dedicated Hikari pool (`TargetDatabaseConnectionService`) and closed after use — there's no shared connection pool between environments, so adding one environment cannot interfere with any other environment's connections. There's no JPA/Hibernate layer in this project — all target-database access is plain JDBC (`JdbcTemplate` over the SQL text the admin defines), so there's no ORM dialect to configure per database type; each `TargetDatabaseType` just carries its JDBC driver class name and URL prefix for validation.

### Adding a PostgreSQL Target Environment

1. Sign in as an `ADMIN` user and open `/QaLite/admin`.
2. Under **Environments**, fill in:
   - **Name** — a short label such as `SIT` or `UAT`
   - **Database Type** — `PostgreSQL`
   - **JDBC URL** — e.g. `jdbc:postgresql://host:5432/database` (do not put credentials in the URL)
   - **Username** / **Password** — an account on that PostgreSQL database
3. Save. The password is encrypted (AES/GCM) before being stored, using the app's `QALITE_SECRET_KEY`.
4. Click **Test** next to the new row under "Current Environments" to confirm QA Lite can reach it.

### Adding a MySQL Target Environment

1. Go to `/QaLite/admin` → **Add Environment**.
2. Set **Database Type** to `MySQL`.
3. **JDBC URL**: `jdbc:mysql://host:3306/database` (add JDBC params like `?useSSL=true` directly in the URL if needed).
4. Fill in the DB username/password — the password is encrypted at rest (AES/GCM, `QALITE_SECRET_KEY`).
5. Click **Test** next to the new row under "Current Environments" to confirm QA Lite can reach it — this opens and closes a short-lived, isolated connection pool for that one environment only; it never touches the app's own storage database or any other environment's connections.

### Adding an Oracle Target Environment

1. Go to `/QaLite/admin` → **Add Environment**.
2. Set **Database Type** to `Oracle`.
3. **JDBC URL**: `jdbc:oracle:thin:@//host:1521/service_name`.
4. Fill in username/password for an account on that Oracle database (a separate account from the app's own storage schema).
5. Save, then click **Test** to confirm connectivity.

### Adding a MongoDB Atlas SQL Target Environment

1. Go to `/QaLite/admin` → **Add Environment**.
2. Set **Database Type** to `MongoDB Atlas SQL`.
3. **JDBC URL**: the Atlas SQL JDBC connection string from your Atlas cluster's connection settings (Atlas SQL interface, not the standard `mongodb+srv://` driver URL).
4. Fill in the Atlas SQL username/password.
5. Save, then click **Test** to confirm connectivity.

## Routes

All routes are prefixed with `/QaLite`.

| Route | Purpose |
| --- | --- |
| `GET /login` | Login page |
| `POST /register` | Create a `QA_USER` account |
| `GET /` | Dashboard (environments/SQL commands allowed for the user) |
| `GET /admin` | Admin page (`ADMIN` role only) |
| `POST /admin/environments` | Add an environment |
| `POST /admin/sql` | Add a SQL command |
| `POST /admin/users/{userId}/permissions` | Update a user's access |

## Deploy to a Server

The built file is fully self-contained — it already includes an embedded web server, so nothing else needs to be installed on the server except Java. Building it (`mvnw package`) needs internet access to download dependencies, so if the target server is offline (e.g. an internal database server with no internet access), **build the WAR on a machine that has internet access and copy only the built file over** — the server itself never needs internet or Maven.

**Requirements on the server:** Java 21+ only, and a dedicated Oracle schema/user for the app — see [One-Time Oracle Setup](#one-time-oracle-setup-apps-own-database) (it creates an isolated user inside the existing Oracle instance, it does not touch any existing data).

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

Once it's running, open `http://SERVER_HOST:8080/QaLite/login`. To change the port, set `SERVER_PORT` in `.env`. For a real deployment, also see [Enabling HTTPS](#enabling-https) — plain HTTP sends login credentials unencrypted even on an internal network.

If the server *does* have internet access, you can instead `git clone` the repo on it and run `./mvnw -DskipTests package` there — same result, just built in place.

## Enabling HTTPS

QA Lite serves plain HTTP by default. Even on an internal company network (no public internet exposure), login credentials and session cookies still cross the wire in the clear over HTTP — any other machine on the same network segment can read them. The app can terminate TLS itself (no separate reverse proxy needed), which is the simplest option for a single internal server.

**1. Get a certificate.** Two options:
- **Self-signed** (fastest, works today, but browsers show a "not secure" warning until the certificate is trusted — acceptable for a small internal user base if you tell people to accept it once, or push it as a trusted certificate via group policy). Generate one with `keytool`, bundled with the JDK:
  ```bash
  keytool -genkeypair -alias qalite -keyalg RSA -keysize 2048 -validity 3650 \
    -storetype PKCS12 -keystore qalite-keystore.p12 -storepass CHOOSE_A_KEYSTORE_PASSWORD \
    -dname "CN=qalite.yourcompany.local, OU=QA, O=YourCompany, C=SA" \
    -ext "san=dns:qalite.yourcompany.local,dns:localhost,ip:SERVER_IP_ADDRESS"
  ```
  Replace `qalite.yourcompany.local` with whatever hostname people will actually type in the browser, and `SERVER_IP_ADDRESS` with the server's internal IP (include both so it works either way). List every hostname/IP anyone will use to reach it in `-ext san=...`, comma-separated — browsers reject a certificate that doesn't list the exact address in the URL bar.
- **Issued by your company's internal CA**, if you have one (e.g. Active Directory Certificate Services) — no browser warnings on domain-joined machines. Ask whoever manages it for a PKCS12 (`.p12`/`.pfx`) certificate+key file for the server's hostname; use that file directly in step 2 instead of the self-signed one.

**2. Configure `.env`** — add these on top of the usual database settings:
```properties
SERVER_PORT=8443
QALITE_SSL_ENABLED=true
QALITE_SSL_KEYSTORE=file:/full/path/to/qalite-keystore.p12
QALITE_SSL_KEYSTORE_PASSWORD=CHOOSE_A_KEYSTORE_PASSWORD
QALITE_SSL_KEYSTORE_TYPE=PKCS12
QALITE_SSL_KEY_ALIAS=qalite
```
Keep the keystore file next to `.env` on the server (outside the repo — it's git-ignored via `*.p12`/`*.jks`) and never commit it. With `QALITE_SSL_ENABLED=true`, the app serves **HTTPS only** on `SERVER_PORT` — there's no separate plain-HTTP port to disable.

**3. Run as usual** (`java -jar qa-lite-0.0.1-SNAPSHOT.war`) and open `https://SERVER_HOST:8443/QaLite/login`. Once TLS is live, `SecurityHeadersFilter` automatically starts sending the `Strict-Transport-Security` header (it already checks `request.isSecure()` — see [Security](#security)), and the session cookie automatically gets the `Secure` flag — nothing else to configure.

If you'd rather also keep plain HTTP available and have it redirect to HTTPS instead of dropping it entirely, that needs a small additional `WebServerFactoryCustomizer` bean (a second Tomcat connector) — ask if you want that added.

## Database Migrations

Flyway migrations live in `src/main/resources/db/migration`:
- `V1__init_schema.sql` — schema
- `V2__seed_sample_data.sql` — seeds the default `admin` account plus demo data (a `qa_user` account, `SIT`/`UAT` sample environments, two sample SQL commands) so the dashboard isn't empty on first run
- `V3__remove_demo_data.sql` — deletes that demo data again (everything V2 seeded except `admin`), so a real deployment doesn't launch with placeholder environments/commands. On a brand-new database both run on first startup, back to back, so the net effect is just the `admin` account with nothing else.

Main tables: `users`, `environments`, `sql_definitions`, `user_allowed_env`, `user_allowed_sql`, `execution_history`.

Add new changes as a new versioned file (e.g. `V4__...sql`) — never edit an already-applied migration on a shared database.

## Security

- Passwords hashed with BCrypt; target DB passwords encrypted (AES/GCM) using `QALITE_SECRET_KEY`.
- Self-registration always creates `QA_USER`; admins have implicit full access.
- `/admin/**` requires `ROLE_ADMIN`; login/register are rate-limited per IP.
- Admin-defined SQL commands can be **any** SQL, including writes and DDL (`INSERT`/`UPDATE`/`DELETE`/`DROP`/...) — there is no read-only restriction. Admins are fully trusted, but remember that any `QA_USER` granted access to a destructive command can run it against that command's environment, so grant destructive commands deliberately.
- Standard security headers (CSP, X-Frame-Options, HSTS once HTTPS is on, etc.) — see [Enabling HTTPS](#enabling-https).

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

## Status

Dashboard SQL execution is fully implemented end-to-end: `POST /execute` (`ExecutionController` → `ExecutionService`) runs the selected SQL against the selected target environment through a short-lived per-environment connection, re-checks the user's environment/SQL permissions server-side (never trusts the UI), binds `:name` parameters through a `PreparedStatement` (no string concatenation), and logs every attempt — success or failure — to `execution_history`.

The demo data (`qa_user`, `SIT`/`UAT` environments, sample SQL commands) that `V2__seed_sample_data.sql` seeds is automatically removed again by `V3__remove_demo_data.sql` (see [Database Migrations](#database-migrations)) — the only account left after a fresh startup is `admin`. Before pointing this at real users, still do the following:

- **Know the `admin` account's password.** `V2__seed_sample_data.sql` only stores its bcrypt hash — the plaintext password is never written anywhere in this repo. If it's not already known, update `users.password_hash` directly with a freshly generated BCrypt hash before exposing the app.
- **Turn on HTTPS** — see [Enabling HTTPS](#enabling-https). Even on an internal-only network, plain HTTP still sends login credentials and session cookies unencrypted to anyone else on the same network segment.
- **Back up `QALITE_SECRET_KEY` somewhere safe.** Losing it makes every stored target-environment password permanently undecryptable (see [Configuration Reference](#configuration-reference-env)).
