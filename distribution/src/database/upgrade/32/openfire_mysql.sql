ALTER TABLE ofMucRoom ADD COLUMN fmucEnabled TINYINT NULL;
ALTER TABLE ofMucRoom ADD COLUMN fmucOutboundNode VARCHAR(255) NULL;
ALTER TABLE ofMucRoom ADD COLUMN fmucOutboundMode TINYINT NULL;
ALTER TABLE ofMucRoom ADD COLUMN fmucInboundNodes VARCHAR(4000) NULL;

UPDATE ofVersion SET version = 32 WHERE name = 'openfire';
