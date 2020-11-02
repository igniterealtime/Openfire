ALTER TABLE ofMucRoom ADD COLUMN fmucEnabled INTEGER;
ALTER TABLE ofMucRoom ADD COLUMN fmucOutboundNode VARCHAR(255);
ALTER TABLE ofMucRoom ADD COLUMN fmucOutboundMode INTEGER;
ALTER TABLE ofMucRoom ADD COLUMN fmucInboundNodes VARCHAR(2000);

UPDATE ofVersion SET version = 32 WHERE name = 'openfire';
