ALTER TABLE ofProperty
  ADD encrypted   INTEGER;

UPDATE ofVersion SET version = 26 WHERE name = 'openfire';
