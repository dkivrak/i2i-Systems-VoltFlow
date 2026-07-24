ALTER TABLE homes
    ADD COLUMN owner_email VARCHAR(320);

UPDATE homes
SET owner_email = LOWER(contact_email)
WHERE owner_email IS NULL;

ALTER TABLE homes
    ALTER COLUMN owner_email SET NOT NULL;

CREATE INDEX idx_homes_owner_email ON homes(owner_email);
