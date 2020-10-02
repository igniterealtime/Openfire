ALTER TABLE ofMucRoom ADD COLUMN fmucEnabled INTEGER NULL;
ALTER TABLE ofMucRoom ADD COLUMN fmucOutboundNode TEXT NULL;
ALTER TABLE ofMucRoom ADD COLUMN fmucOutboundMode INTEGER NULL;
ALTER TABLE ofMucRoom ADD COLUMN fmucInboundNodes TEXT NULL;

UPDATE ofVersion SET version = 32 WHERE name = 'openfire';
