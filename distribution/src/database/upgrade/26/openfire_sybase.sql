/*Encrypted column with default value 0 i.e false*/
ALTER TABLE ofProperty
  ADD encrypted   INTEGER DEFAULT 0

UPDATE ofVersion SET version = 26 WHERE name = 'openfire'
