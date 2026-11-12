-- Counter (box-office) cash sales by Staff: cash shifts, walk-in orders without an account, and voidable counter tickets.

CREATE TABLE shifts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  staff_id BIGINT NOT NULL,
  cinema_id BIGINT NOT NULL,
  status VARCHAR(255) NOT NULL,
  opened_at DATETIME(6) NOT NULL,
  closed_at DATETIME(6),
  opening_cash BIGINT NOT NULL,
  expected_cash BIGINT,
  counted_cash BIGINT,
  note VARCHAR(500),
  -- At most one OPEN shift per staff member, enforced by the database as well as the service.
  open_staff_id BIGINT AS (CASE WHEN status='OPEN' THEN staff_id END) STORED,
  FOREIGN KEY(staff_id) REFERENCES users(id),
  FOREIGN KEY(cinema_id) REFERENCES cinemas(id),
  UNIQUE KEY shifts_one_open(open_staff_id),
  INDEX shifts_cinema_opened(cinema_id, opened_at),
  CONSTRAINT shifts_status CHECK (status IN ('OPEN','CLOSED')),
  CONSTRAINT shifts_cash CHECK (opening_cash >= 0 AND (counted_cash IS NULL OR counted_cash >= 0))
);

-- Walk-in counter orders have no customer account.
ALTER TABLE orders
  MODIFY user_id BIGINT NULL,
  ADD COLUMN channel VARCHAR(255) NOT NULL DEFAULT 'ONLINE',
  ADD COLUMN sold_by BIGINT NULL,
  ADD COLUMN shift_id BIGINT NULL,
  ADD COLUMN cancelled_at DATETIME(6) NULL,
  ADD COLUMN cancelled_by BIGINT NULL,
  ADD COLUMN cancel_reason VARCHAR(500) NULL,
  ADD CONSTRAINT orders_sold_by FOREIGN KEY(sold_by) REFERENCES users(id),
  ADD CONSTRAINT orders_shift FOREIGN KEY(shift_id) REFERENCES shifts(id),
  ADD CONSTRAINT orders_cancelled_by FOREIGN KEY(cancelled_by) REFERENCES users(id),
  ADD CONSTRAINT orders_channel CHECK (channel IN ('ONLINE','COUNTER')),
  ADD INDEX orders_sold_by_status(sold_by, status);

ALTER TABLE payments
  ADD COLUMN received BIGINT NULL,
  ADD COLUMN shift_id BIGINT NULL,
  ADD CONSTRAINT payments_shift FOREIGN KEY(shift_id) REFERENCES shifts(id),
  ADD CONSTRAINT payments_received CHECK (received IS NULL OR received >= amount);

-- A cancelled counter ticket is VOID; only VALID/USED tickets reserve the seat, so the seat can be sold again.
ALTER TABLE tickets
  ADD COLUMN active_seat_id BIGINT AS (CASE WHEN status IN ('VALID','USED') THEN seat_id END) STORED,
  ADD UNIQUE KEY tickets_active_seat(showtime_id, active_seat_id),
  DROP INDEX showtime_id;

-- Points ledger keeps one EARN and at most one REVERSAL row per order.
ALTER TABLE point_ledger
  ADD COLUMN kind VARCHAR(255) NOT NULL DEFAULT 'EARN',
  ADD UNIQUE KEY point_ledger_order_kind(order_id, kind),
  ADD CONSTRAINT point_ledger_kind CHECK (kind IN ('EARN','REVERSAL'));
ALTER TABLE point_ledger DROP INDEX order_id;
