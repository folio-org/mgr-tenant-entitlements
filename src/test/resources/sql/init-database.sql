create database te_it;

create user te_admin with password 'folio123';
grant connect on database te_it to te_admin;
grant all privileges on database te_it to te_admin;

create database kong_it;

create user kong_admin with password 'kong123';
grant connect on database kong_it to kong_admin;
grant all privileges on database kong_it to kong_admin;
