# QA Lite Oracle Setup

QA Lite stores its own data (users, environments, SQL definitions, permissions,
execution history) in an Oracle schema. It does **not** need a brand new
database — it just needs one dedicated, empty user/schema inside the Oracle
instance already running on the server. Flyway creates all the tables inside
that schema automatically the first time the app starts.

## 1. Create A Dedicated Schema (one-time DBA step)

Ask whoever has DBA access on the server's Oracle instance to run this once.
It creates a new user with no access to any other schema's data:

```sql
CREATE USER qalite IDENTIFIED BY "choose-a-strong-password-here";
GRANT CONNECT, RESOURCE TO qalite;
GRANT UNLIMITED TABLESPACE TO qalite;
```

This does not touch, move, or expose any existing data on the server. The new
`qalite` user starts completely empty and cannot see other schemas' tables
unless explicitly granted.

To remove it later, if ever needed:
```sql
DROP USER qalite CASCADE;
```

## 2. Collect The Connection Details

From whoever ran the SQL above, get:
- **Host** and **Port** of the Oracle listener (`localhost`/`1521` if the app
  runs on the same server as Oracle)
- **Service name** (or SID) of the existing database/PDB the user was created in
- The **username** (`qalite`) and the **password** chosen above

## 3. Configure The Application

Edit the `.env` file in the project root (copy from `.env.example`):

```properties
QALITE_DB_URL=jdbc:oracle:thin:@//HOST:PORT/SERVICE_NAME
QALITE_DB_USERNAME=qalite
QALITE_DB_PASSWORD=choose-a-strong-password-here
QALITE_SECRET_KEY=<a separate long random value, encrypts target DB passwords>
```

## 4. Run

See the "Deploy to a Server" section in the main [README](../README.md) —
in short, once `.env` is filled in:

```bash
java -jar qa-lite-0.0.1-SNAPSHOT.war
```

Flyway creates every table from scratch on first startup. The app context path
is `/QaLite`.
