ALTER TABLE ofMucService ADD COLUMN privateKey VARCHAR(255);

UPDATE ofMucService SET privateKey =
    array_to_string(
        array(
            SELECT substring('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789', floor(random() * 62)::int + 1, 1) FROM generate_series(1, 21) WHERE ofMucService.privateKey IS DISTINCT FROM 'something'
        ), ''
    );

ALTER TABLE ofMucService ALTER COLUMN privateKey SET NOT NULL;

UPDATE ofVersion SET version = 38 WHERE name = 'openfire';
