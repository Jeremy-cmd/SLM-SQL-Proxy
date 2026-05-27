CREATE TABLE users
 (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name varchar(20) NOT NULL,
    last_name varchar(20) NOT NULL,
    address varchar(255) NOT NULL
);

CREATE TABLE orders
(
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id integer NOT NULL,
    order_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    amount numeric(10, 2) NOT NULL,

    CONSTRAINT fk_orders_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_orders_user_id ON orders(user_id);

CREATE TABLE items
(
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    sku VARCHAR(20) UNIQUE NOT NULL,
    price numeric(10, 2) NOT NULL,
    description varchar(100)
);

CREATE TABLE order_items
 (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id integer NOT NULL,
    item_id integer NOT NULL,
    quantity integer DEFAULT 1 NOT NULL,

    CONSTRAINT uq_order_item UNIQUE (order_id, item_id),

    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id)
        REFERENCES orders (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_order_items_item
        FOREIGN KEY (item_id)
        REFERENCES items (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_item_id ON order_items(item_id);