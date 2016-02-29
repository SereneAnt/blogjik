# --- !Ups

ALTER TABLE "authors" ADD COLUMN "email" VARCHAR(250) NOT NULL DEFAULT '';

# --- !Downs

ALTER TABLE "authors" DROP COLUMN "email";