-- Runs once, the first time the postgres volume is initialised.
--
-- One database per service, never a shared schema with a prefix. Services own their data
-- outright: auth-service cannot read a user profile and user-service cannot read a password
-- hash, which is enforced by the connection they are given rather than by convention.
--
-- Locally they share one PostgreSQL instance because running four is pointless on a laptop.
-- In production they would be separate instances; nothing in the application code knows the
-- difference, because each service only ever sees its own DB_URL.
--
-- auth-service's database comes from POSTGRES_DB in the compose file. The rest are created here.

CREATE DATABASE tenantbase_users;
