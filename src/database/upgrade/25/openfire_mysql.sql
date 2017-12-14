
set @exist := (select count(*) from information_schema.statistics where table_name = 'ofMucConversationLog' and index_name = 'ofMucConvLog_msg_id' and table_schema = database());
set @sqlstmt := if( @exist > 0, 'select ''INFO: Index already exists.''', 'create index ofMucConvLog_msg_id on ofMucConversationLog (messageID)');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;

UPDATE ofVersion SET version = 25 WHERE name = 'openfire';
