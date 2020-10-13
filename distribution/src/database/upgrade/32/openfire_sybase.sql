ALTER TABLE ofMucRoom ADD COLUMN fmucEnabled INT NULL;
ALTER TABLE ofMucRoom ADD COLUMN fmucOutboundNode NVARCHAR(255) NULL;
ALTER TABLE ofMucRoom ADD COLUMN fmucOutboundMode INT NULL;
ALTER TABLE ofMucRoom ADD COLUMN fmucInboundNodes NVARCHAR(2000) NULL;

UPDATE ofVersion SET version = 32 WHERE name = 'openfire';
