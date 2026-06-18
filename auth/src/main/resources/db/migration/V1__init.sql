-- Auth/User service: credential store (email + password only for now).
CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(32)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

-- Email is the login identity; stored normalized (lower-cased) by the app.
CREATE UNIQUE INDEX ux_users_email ON users (email);
