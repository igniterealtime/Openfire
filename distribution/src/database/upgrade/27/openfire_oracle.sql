ALTER TABLE ofProperty
  ADD iv   CHAR(24);

UPDATE ofVersion SET version = 27 WHERE name = 'openfire';
