ALTER TABLE ofMucService ADD COLUMN privateKey VARCHAR(255);

UPDATE ofMucService SET privateKey = (
    SELECT
        SUBSTR('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 62) + 1, 1) ||
        SUBSTR('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 62) + 1, 1) ||
        SUBSTR('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 62) + 1, 1) ||
        SUBSTR('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 62) + 1, 1) ||
        SUBSTR('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 62) + 1, 1) ||
        SUBSTR('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 62) + 1, 1) ||
        SUBSTR('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 62) + 1, 1) ||
        SUBSTR('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 62) + 1, 1) ||
        SUBSTR('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 62) + 1, 1) ||
        SUBSTR('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 62) + 1, 1) ||
        SUBSTR('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 62) + 1, 1) ||
        SUBSTR('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 62) + 1, 1) ||
        SUBSTR('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 62) + 1, 1) ||
        SUBSTR('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 62) + 1, 1) ||
        SUBSTR('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 62) + 1, 1) ||
        SUBSTR('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 62) + 1, 1) ||
        SUBSTR('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 62) + 1, 1) ||
        SUBSTR('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 62) + 1, 1) ||
        SUBSTR('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 62) + 1, 1) ||
        SUBSTR('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 62) + 1, 1) ||
        SUBSTR('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 62) + 1, 1) ||
        SUBSTR('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 62) + 1, 1)
    FROM SYSIBM.SYSDUMMY1
);

ALTER TABLE ofMucService ALTER COLUMN privateKey SET NOT NULL;

UPDATE ofVersion SET version = 38 WHERE name = 'openfire';
