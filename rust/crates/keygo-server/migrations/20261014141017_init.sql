CREATE TABLE users
(
    id         UUID PRIMARY KEY,            -- client-generated, equals local user_id
    username   TEXT        NOT NULL UNIQUE, -- normalized
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNLOGGED TABLE pending_registrations
(
    id         bytea PRIMARY KEY,           -- registration_id, 16 random bytes
    user_id    uuid        NOT NULL UNIQUE,
    username   text        NOT NULL UNIQUE, -- the reservation
    -- invite_id  text REFERENCES invites,
    expires_at timestamptz NOT NULL
);