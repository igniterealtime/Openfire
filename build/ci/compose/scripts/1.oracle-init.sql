alter session set container=FREEPDB1;
create user openfire identified by SecurePa55w0rd;
grant connect, resource to openfire;
grant unlimited tablespace to openfire;
alter session set current_schema = openfire;
CREATE DATABASE openfire;
