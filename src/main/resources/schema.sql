CREATE TABLE IF NOT EXISTS users
 (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name varchar(20) NOT NULL,
    last_name varchar(20) NOT NULL,
    address varchar(255) NOT NULL
);