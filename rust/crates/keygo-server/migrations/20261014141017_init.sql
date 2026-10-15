CREATE TABLE users
(
    id         UUID PRIMARY KEY,            -- client-generated, equals local user_id
    username   TEXT        NOT NULL UNIQUE, -- normalized
    created_at timestamptz NOT NULL DEFAULT now()
);

create table opaque_records
(
    user_id    uuid primary key references users on delete cascade,
    suite      text        not null,
    record     bytea       not null,
    updated_at timestamptz not null
);

CREATE UNLOGGED TABLE pending_registrations
(
    id         bytea PRIMARY KEY,           -- registration_id, 16 random bytes
    user_id    uuid        NOT NULL UNIQUE,
    username   text        NOT NULL UNIQUE, -- the reservation
    -- invite_id  text REFERENCES invites,
    expires_at timestamptz NOT NULL
);

CREATE INDEX pending_registrations_expires_at_idx ON pending_registrations (expires_at);

CREATE UNLOGGED TABLE login_states
(
    id         bytea PRIMARY KEY,    -- login_id, 16 random bytes
    user_id    uuid,                 -- null on the fake path
    purpose    text        NOT NULL CHECK (purpose IN ('login', 'step_up')),
    state      bytea       NOT NULL, -- serialized ServerLogin
    expires_at timestamptz NOT NULL
);

CREATE INDEX login_states_expires_at_idx ON login_states (expires_at);
