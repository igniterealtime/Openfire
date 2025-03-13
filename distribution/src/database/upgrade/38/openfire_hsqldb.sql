ALTER TABLE ofMucService ADD COLUMN privateKey VARCHAR(255);

UPDATE ofMucService SET privateKey =
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', RAND() * 62 + 1, 1) ||
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', RAND() * 62 + 1, 1) ||
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', RAND() * 62 + 1, 1) ||
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', RAND() * 62 + 1, 1) ||
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', RAND() * 62 + 1, 1) ||
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', RAND() * 62 + 1, 1) ||
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', RAND() * 62 + 1, 1) ||
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', RAND() * 62 + 1, 1) ||
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', RAND() * 62 + 1, 1) ||
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', RAND() * 62 + 1, 1) ||
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', RAND() * 62 + 1, 1) ||
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', RAND() * 62 + 1, 1) ||
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', RAND() * 62 + 1, 1) ||
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', RAND() * 62 + 1, 1) ||
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', RAND() * 62 + 1, 1) ||
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', RAND() * 62 + 1, 1) ||
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', RAND() * 62 + 1, 1) ||
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', RAND() * 62 + 1, 1) ||
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', RAND() * 62 + 1, 1) ||
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', RAND() * 62 + 1, 1) ||
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', RAND() * 62 + 1, 1) ||
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', RAND() * 62 + 1, 1) ||
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', RAND() * 62 + 1, 1);

ALTER TABLE ofMucService ALTER COLUMN privateKey SET NOT NULL;

UPDATE ofVersion SET version = 38 WHERE name = 'openfire';
