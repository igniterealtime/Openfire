--设置JDBC连接方式
insert into OFPROPERTY (NAME, PROPVALUE)values 
('jdbcProvider.connectionString', 'jdbc:oracle:thin:@10.35.246.178:1521:orcl'),
('jdbcProvider.driver', 'oracle.jdbc.driver.OracleDriver'),

--设置管理员账号
insert into OFPROPERTY (NAME, PROPVALUE)values
('admin.authorizedJIDs', 'admin@localhost');

--自定义用户认证
insert into OFPROPERTY (NAME, PROPVALUE)values
('jdbcAuthProvider.useConnectionProvider', 'true'),
('jdbcAuthProvider.passwordSQL', 'select ''123456'' as pwd from t_sys_user t where t.code=?'),
('jdbcAuthProvider.passwordType', 'plain');

--自定义用户管理
insert into OFPROPERTY (NAME, PROPVALUE)values 
('jdbcUserProvider.allUsersSQL', 'select code from t_sys_user t'),
('jdbcUserProvider.nameField', 'name'),
('jdbcUserProvider.searchSQL', 'select code from t_sys_user t where code=?');
('jdbcUserProvider.useConnectionProvider', 'true'),
('jdbcUserProvider.emailField', 'email'),
('jdbcUserProvider.userCountSQL', 'select count(*) from t_sys_user t'),
('jdbcUserProvider.loadUserSQL', 'select t.name,t.email,t.code from t_sys_user t where t.code=?'),
('jdbcUserProvider.usernameField', 'code');

--使用不同的数据源
update OFPROPERTY 
set PROPVALUE='org.jivesoftware.openfire.user.JDBCUserProvider'
where NAME='provider.user.className';

update OFPROPERTY 
set PROPVALUE='org.jivesoftware.openfire.auth.JDBCAuthProvider'
where NAME='provider.auth.className';
commit;