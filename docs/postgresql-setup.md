# QA Lite PostgreSQL Setup

Use PostgreSQL as the QA Lite application database. This database stores users,
environments, SQL definitions, SQL permissions, and execution history.

## 1. Create The Tables

Flyway creates the tables automatically when the application starts. The first
migration is:

```text
src/main/resources/db/migration/V1__init_schema.sql
```

The current minimal schema creates:

```text
users
environments
sql_definitions
user_allowed_sql
user_allowed_env
execution_history
```

## 2. Configure The Application

Edit the `.env` file in the project root:

```text
QALITE_DB_URL=jdbc:postgresql://HOST:PORT/DATABASE?sslmode=require
QALITE_DB_USERNAME=USERNAME
QALITE_DB_PASSWORD=PASSWORD
QALITE_DB_POOL_SIZE=10
```

Use the exact host, port, database name, username, and password from your cloud
PostgreSQL provider. The `.env` file is ignored by Git because it contains
secrets.

## 3. Run

```powershell
.\mvnw.cmd spring-boot:run
```

The app context path is:

```text
/QaLite
```
