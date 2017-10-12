CREATE TABLE IF NOT EXISTS vegetables(
  id        SERIAL NOT NULL PRIMARY KEY,
  name      VARCHAR(40) NOT NULL,
  amount    INT
);

DELETE FROM vegetables;

INSERT INTO vegetables (name, amount) values ('Carrots', 10);
INSERT INTO vegetables (name, amount) values ('Tomatoes', 10);
INSERT INTO vegetables (name, amount) values ('Onions', 10);
