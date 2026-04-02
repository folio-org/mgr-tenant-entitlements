-- 1. Setup for te_it
CREATE DATABASE te_it;
CREATE USER te_admin WITH PASSWORD 'folio123';
ALTER DATABASE te_it OWNER TO te_admin;


-- 2. Setup for kong_it
CREATE DATABASE kong_it;
CREATE USER kong_admin WITH PASSWORD 'kong123';
ALTER DATABASE kong_it OWNER TO kong_admin;