create table users
(
    id         uuid primary key,            -- client-generated, equals local user_id
    username   text        not null unique, -- normalized
    created_at timestamptz not null default now()
);

create table invites
(
    code_hash  bytea primary key,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null
);

create table opaque_records
(
    user_id    uuid primary key references users on delete cascade,
    suite      text        not null,
    record     bytea       not null,
    updated_at timestamptz not null
);

create unlogged table pending_registrations
(
    id          bytea primary key,           -- registration_id, 16 random bytes
    user_id     uuid        not null unique,
    username    text        not null unique, -- the reservation
    invite_hash bytea,
    expires_at  timestamptz not null
);
create index pending_registrations_expires_at_idx on pending_registrations (expires_at);

create unlogged table login_states
(
    id         bytea primary key,    -- login_id, 16 random bytes
    user_id    uuid,                 -- null on the fake path
    purpose    text        not null check (purpose in ('login', 'step_up')),
    state      bytea       not null, -- serialized ServerLogin
    expires_at timestamptz not null
);
create index login_states_expires_at_idx on login_states (expires_at);
