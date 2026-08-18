DO $$
BEGIN
    IF EXISTS (SELECT lower(trim(email)) FROM users GROUP BY lower(trim(email)) HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Migração interrompida: existem e-mails que colidem após canonicalização.';
    END IF;
    IF EXISTS (SELECT lower(trim(username)) FROM users GROUP BY lower(trim(username)) HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Migração interrompida: existem usernames que colidem após canonicalização.';
    END IF;
END $$;

UPDATE users SET email = lower(trim(email)), username = lower(trim(username));

CREATE UNIQUE INDEX users_email_case_insensitive_key ON users (lower(email));
CREATE UNIQUE INDEX users_username_case_insensitive_key ON users (lower(username));

ALTER TABLE users
    ADD COLUMN accepted_terms_version varchar(64),
    ADD COLUMN terms_accepted_at timestamp(6) without time zone,
    ADD COLUMN accepted_privacy_version varchar(64),
    ADD COLUMN privacy_accepted_at timestamp(6) without time zone;
