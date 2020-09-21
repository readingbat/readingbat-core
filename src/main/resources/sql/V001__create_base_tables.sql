CREATE TABLE users
(
    id                  BIGSERIAL UNIQUE PRIMARY KEY,
    created             TIMESTAMP DEFAULT NOW(),
    updated             TIMESTAMP DEFAULT NOW(),
    user_id             varchar(25) UNIQUE,
    email               TEXT NOT NULL UNIQUE,
    name                TEXT NOT NULL,
    salt                TEXT NOT NULL,
    digest              TEXT NOT NULL,
    enrolled_class_code TEXT NOT NULL
);

/*CREATE UNIQUE INDEX users1_index ON users (user_id);
CREATE UNIQUE INDEX users2_index ON users (email);*/

CREATE TABLE browser_sessions
(
    id                          BIGSERIAL UNIQUE PRIMARY KEY,
    created                     TIMESTAMP     DEFAULT NOW(),
    updated                     TIMESTAMP     DEFAULT NOW(),
    session_id                  TEXT NOT NULL,
    user_ref                    INTEGER       DEFAULT 0,
    active_class_code           TEXT NOT NULL DEFAULT '',
    previous_teacher_class_code TEXT NOT NULL DEFAULT '',
    CONSTRAINT browser_sessions_unique unique (session_id)
);

CREATE TABLE user_challenge_info
(
    id           BIGSERIAL PRIMARY KEY,
    created      TIMESTAMP     DEFAULT NOW(),
    updated      TIMESTAMP     DEFAULT NOW(),
    user_ref     INTEGER REFERENCES users ON DELETE CASCADE,
    md5          TEXT NOT NULL,
    correct      BOOLEAN       DEFAULT false,
    likedislike  SMALLINT      DEFAULT 0,
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
    CONSTRAINT user_answer_history_unique unique (user_ref, md5)
);

CREATE TABLE session_challenge_info
(
    id           BIGSERIAL PRIMARY KEY,
    created      TIMESTAMP     DEFAULT NOW(),
    updated      TIMESTAMP     DEFAULT NOW(),
    session_ref  INTEGER REFERENCES browser_sessions ON DELETE CASCADE,
    md5          TEXT NOT NULL,
    correct      BOOLEAN       DEFAULT false,
    likedislike  SMALLINT      DEFAULT 0,
    answers_json TEXT NOT NULL DEFAULT '',
    CONSTRAINT session_challenge_info_unique unique (session_ref, md5)
);

/*
CREATE TABLE session_correct_answers
(
    id          BIGSERIAL PRIMARY KEY,
    created     TIMESTAMP DEFAULT NOW(),
    session_ref INTEGER REFERENCES user_browser_sessions ON DELETE CASCADE
);

CREATE TABLE user_likes_dislikes
(
    id       BIGSERIAL PRIMARY KEY,
    created  TIMESTAMP DEFAULT NOW(),
    user_ref INTEGER REFERENCES users ON DELETE CASCADE
);

CREATE TABLE session_likes_dislikes
(
    id           BIGSERIAL PRIMARY KEY,
    created      TIMESTAMP DEFAULT NOW(),
    session_ref  INTEGER REFERENCES user_browser_sessions ON DELETE CASCADE,
    like_dislike INTEGER     DEFAULT 0
);

CREATE TABLE requests
(
    id         BIGSERIAL PRIMARY KEY,
    created    TIMESTAMP DEFAULT NOW(),
    user_ref   INTEGER REFERENCES users ON DELETE CASCADE,
    session_id VARCHAR(15) NOT NULL,
    remote     TEXT        NOT NULL,
    path       TEXT        NOT NULL
);

CREATE UNIQUE INDEX requests1_index ON requests (user_ref);
*/

/*
CREATE TABLE usergroups
(
    usergroup_id    SERIAL PRIMARY KEY,
    servergroup_ref INTEGER REFERENCES servergroups ON DELETE CASCADE,
    created         TIMESTAMP   DEFAULT NOW(),
    name            TEXT NOT NULL,
    domain          TEXT NOT NULL,
    loglevel        TEXT NOT NULL DEFAULT '',
    maxlogsize      INTEGER,
    description     TEXT NOT NULL DEFAULT ''
);

CREATE UNIQUE INDEX usergroups1_index
    ON usergroups (name);


CREATE TABLE users
(
    user_id         SERIAL PRIMARY KEY,
    usergroup_ref   INTEGER REFERENCES usergroups ON DELETE CASCADE,
    created         TIMESTAMP   DEFAULT NOW(),
    username        TEXT NOT NULL,
    firstname       TEXT NOT NULL,
    lastname        TEXT NOT NULL,
    hashedpassword  TEXT NOT NULL,
    permissionlevel INTEGER       DEFAULT 0,
    email           TEXT NOT NULL DEFAULT '',
    loglevel        TEXT NOT NULL DEFAULT '',
    maxlogsize      INTEGER
);

CREATE UNIQUE INDEX users1_index
    ON users (username);
CREATE UNIQUE INDEX users2_index
    ON users (email);


CREATE TABLE mbeanserverfilters
(
    filter_id     SERIAL PRIMARY KEY,
    usergroup_ref INTEGER REFERENCES usergroups ON DELETE CASCADE,
    created       TIMESTAMP   DEFAULT NOW(),
    name          TEXT NOT NULL,
    filter        TEXT NOT NULL,
    description   TEXT NOT NULL DEFAULT ''
);

CREATE UNIQUE INDEX mbeanserverfilters1_index
    ON mbeanserverfilters (usergroup_ref, name);


CREATE TABLE endpoints
(
    endpoint_id   SERIAL PRIMARY KEY,
    usergroup_ref INTEGER REFERENCES usergroups ON DELETE CASCADE,
    created       TIMESTAMP      DEFAULT NOW(),
    name          TEXT    NOT NULL,
    username      TEXT    NOT NULL,
    path          TEXT    NOT NULL,
    jsonwrapped   BOOLEAN NOT NULL DEFAULT FALSE,
    description   TEXT    NOT NULL DEFAULT ''
);

CREATE UNIQUE INDEX endpoints1_index
    ON endpoints (usergroup_ref, name);


CREATE TABLE notificationlisteners
(
    listener_id   SERIAL PRIMARY KEY,
    usergroup_ref INTEGER REFERENCES usergroups ON DELETE CASCADE,
    created       TIMESTAMP      DEFAULT NOW(),
    name          TEXT    NOT NULL,
    filter        TEXT    NOT NULL DEFAULT '',
    lambdatext    TEXT    NOT NULL,
    outputsize    INTEGER          DEFAULT 10,
    paused        BOOLEAN NOT NULL DEFAULT FALSE,
    description   TEXT    NOT NULL DEFAULT ''
);

CREATE UNIQUE INDEX notificationlisteners1_index
    ON notificationlisteners (usergroup_ref, name);


CREATE TABLE mbeanbindings
(
    mbeanbinding_id SERIAL PRIMARY KEY,
    listener_ref    INTEGER REFERENCES notificationlisteners ON DELETE CASCADE,
    server_ref      INTEGER REFERENCES mbeanserveraliases ON DELETE CASCADE,
    created         TIMESTAMP DEFAULT NOW(),
    objectname      TEXT NOT NULL
);

CREATE UNIQUE INDEX mbeanbindings1_index
    ON mbeanbindings (listener_ref, server_ref, objectname);


CREATE TABLE bindingqueries
(
    bindingquery_id SERIAL PRIMARY KEY,
    listener_ref    INTEGER REFERENCES notificationlisteners ON DELETE CASCADE,
    usergroup_ref   INTEGER REFERENCES usergroups ON DELETE CASCADE,
    created         TIMESTAMP   DEFAULT NOW(),
    serverquery     TEXT NOT NULL,
    objectnamequery TEXT NOT NULL,
    filter          TEXT NOT NULL,
    description     TEXT NOT NULL DEFAULT ''
);
*/