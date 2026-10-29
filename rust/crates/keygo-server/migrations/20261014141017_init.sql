create table users
(
    id         uuid primary key,            -- client-generated, equals local user_id
    username   text        not null unique, -- normalized
    created_at timestamptz not null default now()
);

create table devices
(
    id           uuid primary key,
    user_id      uuid        not null references users on delete cascade,
    name         text        not null,
    platform     text        not null,
    app_version  text        not null,
    created_at   timestamptz not null default now(),
    last_seen_at timestamptz not null default now()
);
create index devices_user_id_idx on devices (user_id);

create table sessions
(
    id            uuid primary key,
    device_id     uuid        not null references devices on delete cascade,
    dpop_jkt      bytea       not null,
    auth_time     timestamptz not null,
    created_at    timestamptz not null default now(),
    expires_at    timestamptz not null,
    revoked_at    timestamptz,
    revoke_reason text
);
create index sessions_device_id_idx on sessions (device_id);
create unique index sessions_one_live_per_device on sessions (device_id) where revoked_at is null;

create table refresh_tokens
(
    token_hash bytea primary key,
    session_id uuid        not null references sessions on delete cascade,
    issued_at  timestamptz not null default now(),
    expires_at timestamptz not null,
    rotated_at timestamptz
);
create index refresh_tokens_session_id_idx on refresh_tokens (session_id);

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
