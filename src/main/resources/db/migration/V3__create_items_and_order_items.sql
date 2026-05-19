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