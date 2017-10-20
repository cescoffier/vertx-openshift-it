CREATE TABLE vegetables (
  id        NUMBER GENERATED ALWAYS as IDENTITY(START with 1 INCREMENT by 1),
  name      VARCHAR2(40) NOT NULL,
  amount    INT,
  CONSTRAINT vegetables_pk PRIMARY KEY (id));

DELETE FROM vegetables;

INSERT INTO vegetables (name, amount) values ('Carrots', 10);
INSERT INTO vegetables (name, amount) values ('Tomatoes', 10);
INSERT INTO vegetables (name, amount) values ('Onions', 10);
