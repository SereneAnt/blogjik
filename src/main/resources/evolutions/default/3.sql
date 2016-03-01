# --- !Ups

ALTER TABLE "posts" ADD COLUMN "likes" INTEGER NOT NULL DEFAULT 0;

# --- !Downs

ALTER TABLE "posts" DROP COLUMN "likes";