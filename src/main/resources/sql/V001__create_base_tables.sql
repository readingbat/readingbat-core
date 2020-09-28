CREATE TABLE browser_sessions
(
    id         BIGSERIAL UNIQUE PRIMARY KEY,
    created    TIMESTAMP DEFAULT NOW(),
    session_id TEXT NOT NULL UNIQUE,
    CONSTRAINT browser_sessions_unique unique (session_id)
);

CREATE TABLE session_challenge_info
(
    id           BIGSERIAL PRIMARY KEY,
    created      TIMESTAMP     DEFAULT NOW(),
    updated      TIMESTAMP     DEFAULT NOW(),
    session_ref  INTEGER REFERENCES browser_sessions ON DELETE CASCADE,
    md5          TEXT NOT NULL,
    all_correct  BOOLEAN       DEFAULT false,
    like_dislike SMALLINT      DEFAULT 0,
    answers_json TEXT NOT NULL DEFAULT '',
    CONSTRAINT session_challenge_info_unique unique (session_ref, md5)
);

CREATE TABLE session_answer_history
(
    id                 BIGSERIAL PRIMARY KEY,
    created            TIMESTAMP DEFAULT NOW(),
    updated            TIMESTAMP DEFAULT NOW(),
    session_ref        INTEGER REFERENCES browser_sessions ON DELETE CASCADE,
    md5                TEXT NOT NULL,
    invocation         TEXT NOT NULL,
    correct            BOOLEAN,
    incorrect_attempts INTEGER,
    history_json       TEXT NOT NULL,
    CONSTRAINT session_answer_history_unique unique (session_ref, md5, invocation)
);

CREATE TABLE users
(
    id                  BIGSERIAL UNIQUE PRIMARY KEY,
    created             TIMESTAMP DEFAULT NOW(),
    updated             TIMESTAMP DEFAULT NOW(),
    user_id             TEXT NOT NULL UNIQUE,
    email               TEXT NOT NULL UNIQUE,
    name                TEXT NOT NULL,
    salt                TEXT NOT NULL,
    digest              TEXT NOT NULL,
    enrolled_class_code TEXT NOT NULL,
    default_language    TEXT NOT NULL
);

CREATE TABLE user_sessions
(
    id                          BIGSERIAL UNIQUE PRIMARY KEY,
    created                     TIMESTAMP     DEFAULT NOW(),
    updated                     TIMESTAMP     DEFAULT NOW(),
    session_ref                 INTEGER REFERENCES browser_sessions ON DELETE CASCADE,
    user_ref                    INTEGER REFERENCES users ON DELETE CASCADE,
    active_class_code           TEXT NOT NULL DEFAULT '',
    previous_teacher_class_code TEXT NOT NULL DEFAULT '',
    CONSTRAINT user_sessions_unique unique (session_ref)
);

CREATE TABLE user_challenge_info
(
    id           BIGSERIAL PRIMARY KEY,
    created      TIMESTAMP     DEFAULT NOW(),
    updated      TIMESTAMP     DEFAULT NOW(),
    user_ref     INTEGER REFERENCES users ON DELETE CASCADE,
    md5          TEXT NOT NULL,
    all_correct  BOOLEAN       DEFAULT false,
    like_dislike SMALLINT      DEFAULT 0,
    answers_json TEXT NOT NULL DEFAULT '',
    CONSTRAINT user_challenge_info_unique unique (user_ref, md5)
);

CREATE TABLE user_answer_history
(
    id                 BIGSERIAL PRIMARY KEY,
    created            TIMESTAMP DEFAULT NOW(),
    updated            TIMESTAMP DEFAULT NOW(),
    user_ref           INTEGER REFERENCES users ON DELETE CASCADE,
    md5                TEXT NOT NULL,
    invocation         TEXT NOT NULL,
    correct            BOOLEAN,
    incorrect_attempts INTEGER,
    history_json       TEXT NOT NULL,
    CONSTRAINT user_answer_history_unique unique (user_ref, md5, invocation)
);

CREATE TABLE classes
(
    id          BIGSERIAL PRIMARY KEY,
    created     TIMESTAMP DEFAULT NOW(),
    updated     TIMESTAMP DEFAULT NOW(),
    user_ref    INTEGER REFERENCES users ON DELETE CASCADE,
    class_code  TEXT NOT NULL UNIQUE,
    description TEXT NOT NULL
);

CREATE TABLE enrollees
(
    id          BIGSERIAL PRIMARY KEY,
    created     TIMESTAMP DEFAULT NOW(),
    classes_ref INTEGER REFERENCES classes ON DELETE CASCADE,
    user_ref    INTEGER REFERENCES users ON DELETE CASCADE,
    CONSTRAINT enrollees_unique unique (classes_ref, user_ref)
);

CREATE TABLE password_resets
(
    id       BIGSERIAL PRIMARY KEY,
    created  TIMESTAMP DEFAULT NOW(),
    updated  TIMESTAMP DEFAULT NOW(),
    user_ref INTEGER REFERENCES users ON DELETE CASCADE UNIQUE,
    reset_id TEXT NOT NULL UNIQUE,
    email    TEXT NOT NULL,
    CONSTRAINT password_resets_unique unique (user_ref)
);