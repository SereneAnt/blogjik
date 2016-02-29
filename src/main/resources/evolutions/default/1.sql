# --- !Ups

CREATE TABLE "authors" (
  "author_id"          VARCHAR(250)      NOT NULL PRIMARY KEY,
  "name"               VARCHAR(250)      NOT NULL
);

CREATE TABLE "details" (
  "author_id"          VARCHAR(250)      NOT NULL PRIMARY KEY,
  "login"              VARCHAR(250)      NOT NULL,
  "password"           VARCHAR(250)      NOT NULL
);

CREATE TABLE "posts" (
  "post_id"            VARCHAR(250)      NOT NULL PRIMARY KEY,
  "author_id"          VARCHAR(250)      NOT NULL REFERENCES "authors" ("author_id"),
  "title"              VARCHAR(250)      NOT NULL,
  "body"               VARCHAR(2000)     NOT NULL,
  "created"            TIMESTAMPTZ       NOT NULL,
  "updated"            TIMESTAMPTZ
);



# --- !Downs

DROP TABLE "authors";
DROP TABLE "details";
DROP TABLE "posts";
