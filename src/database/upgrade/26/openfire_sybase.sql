ALTER TABLE ofProperty
  ADD encrypted   INTEGER      NOT NULL DEFAULT 0;

UPDATE ofVersion SET version = 26 WHERE name = 'openfire';