ALTER TABLE ofProperty
  ADD iv   CHAR(24);

UPDATE ofVersion SET version = 29 WHERE name = 'openfire';
