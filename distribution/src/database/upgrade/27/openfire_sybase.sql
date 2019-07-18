ALTER TABLE ofProperty
  ADD iv   CHAR(24) NULL

UPDATE ofVersion SET version = 27 WHERE name = 'openfire'
