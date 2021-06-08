ALTER TABLE ofMucRoom ADD fmucEnabled INT NULL;
ALTER TABLE ofMucRoom ADD fmucOutboundNode NVARCHAR(255) NULL;
ALTER TABLE ofMucRoom ADD fmucOutboundMode INT NULL;
ALTER TABLE ofMucRoom ADD fmucInboundNodes NVARCHAR(2000) NULL;

UPDATE ofVersion SET version = 32 WHERE name = 'openfire';
