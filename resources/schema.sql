CREATE TABLE pessoa
(
    apelido    TEXT UNIQUE NOT NULL PRIMARY KEY,
    nome       TEXT        NOT NULL,
    nascimento DATE        NOT NULL
);
CREATE TABLE stack
(
    ident  TEXT NOT NULL,
    pessoa TEXT NOT NULL REFERENCES pessoa (apelido),
    PRIMARY KEY (ident, pessoa)
);
