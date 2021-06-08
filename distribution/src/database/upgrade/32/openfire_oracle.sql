ALTER TABLE ofMucRoom ADD fmucEnabled INTEGER NULL;
ALTER TABLE ofMucRoom ADD fmucOutboundNode VARCHAR2(255) NULL;
ALTER TABLE ofMucRoom ADD fmucOutboundMode INTEGER NULL;
ALTER TABLE ofMucRoom ADD fmucInboundNodes VARCHAR2(4000) NULL;

UPDATE ofVersion SET version = 32 WHERE name = 'openfire';
