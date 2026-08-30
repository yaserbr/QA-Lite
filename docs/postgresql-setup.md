# Configuring A PostgreSQL Target Environment

QA Lite's own data lives in Oracle (see [oracle-setup.md](oracle-setup.md)).
PostgreSQL is one of the database types QA Lite can query *as a target
environment* — a QA/SIT/UAT database that admins connect through the Admin
page so testers can run predefined read-only SQL commands against it.

## Add A PostgreSQL Environment

1. Sign in as an `ADMIN` user and open `/QaLite/admin`.
2. Under **Environments**, fill in:
   - **Name** — a short label such as `SIT` or `UAT`
   - **Database Type** — `PostgreSQL`
   - **JDBC URL** — e.g. `jdbc:postgresql://host:5432/database` (do not put
     credentials in the URL)
   - **Username** / **Password** — a read-only account on that PostgreSQL
     database
3. Save. The password is encrypted (AES/GCM) before being stored, using the
   app's `QALITE_SECRET_KEY`.

The connection QA Lite opens to this environment is read-only and pooled
separately per environment (see `TargetDatabaseConnectionService`).
