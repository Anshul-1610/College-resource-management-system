-- ============================================================
--  College Event and Resources Management  -  schema.sql
--  Database : MySQL 8.0
--  Run the whole script once in MySQL Workbench / MySQL Shell.
--  WARNING: re-running it resets these 3 tables to the sample data.
-- ============================================================

-- A fresh database, so it cannot clash with older tables you already have
CREATE DATABASE IF NOT EXISTS event_resource_db;
USE event_resource_db;

-- Remove old versions of this project's objects (safe to re-run)
DROP VIEW  IF EXISTS booking_details;
DROP VIEW  IF EXISTS resource_availability;
DROP TABLE IF EXISTS bookings;
DROP TABLE IF EXISTS resources;
DROP TABLE IF EXISTS events;

-- ------------------------------------------------------------
-- EVENTS
-- ------------------------------------------------------------
CREATE TABLE events (
    event_id    INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    event_date  DATE NULL,
    venue       VARCHAR(150) NULL,
    organizer   VARCHAR(150) NULL,
    CONSTRAINT chk_event_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- RESOURCES  (quantity = total units owned)
-- Available units and status are NOT stored; see the view below.
-- ------------------------------------------------------------
CREATE TABLE resources (
    resource_id INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    type        VARCHAR(30)  NOT NULL DEFAULT 'Other',
    quantity    INT NOT NULL DEFAULT 1,
    CONSTRAINT chk_resource_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_resource_type CHECK (type IN ('Hall', 'Projector', 'Chair', 'Microphone', 'Other')),
    CONSTRAINT chk_resource_qty  CHECK (quantity >= 0)
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- BOOKINGS  (one event reserves some quantity of one resource)
--   Deleting an event cancels its bookings (CASCADE).
--   A resource that still has bookings cannot be deleted (RESTRICT).
-- ------------------------------------------------------------
CREATE TABLE bookings (
    booking_id   INT AUTO_INCREMENT PRIMARY KEY,
    event_id     INT NOT NULL,
    resource_id  INT NOT NULL,
    quantity     INT NOT NULL,
    booking_date DATE NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_booking_qty CHECK (quantity > 0),
    CONSTRAINT fk_booking_event    FOREIGN KEY (event_id)    REFERENCES events(event_id)       ON DELETE CASCADE,
    CONSTRAINT fk_booking_resource FOREIGN KEY (resource_id) REFERENCES resources(resource_id) ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE INDEX idx_bookings_event    ON bookings(event_id);
CREATE INDEX idx_bookings_resource ON bookings(resource_id);

-- ------------------------------------------------------------
-- VIEW: stock, booked, available and status per resource
-- ------------------------------------------------------------
CREATE VIEW resource_availability AS
SELECT  r.resource_id,
        r.name,
        r.type,
        r.quantity                                   AS total_qty,
        COALESCE(SUM(b.quantity), 0)                 AS booked_qty,
        r.quantity - COALESCE(SUM(b.quantity), 0)    AS available_qty,
        CASE WHEN r.quantity - COALESCE(SUM(b.quantity), 0) > 0
             THEN 'Available' ELSE 'In Use' END      AS status
FROM    resources r
LEFT JOIN bookings b ON b.resource_id = r.resource_id
GROUP BY r.resource_id, r.name, r.type, r.quantity;

-- ------------------------------------------------------------
-- VIEW: bookings with event and resource names
-- ------------------------------------------------------------
CREATE VIEW booking_details AS
SELECT  b.booking_id,
        b.event_id,
        e.name          AS event_name,
        b.resource_id,
        r.name          AS resource_name,
        b.quantity,
        b.booking_date,
        b.created_at
FROM    bookings b
JOIN    events    e ON e.event_id    = b.event_id
JOIN    resources r ON r.resource_id = b.resource_id;

-- ------------------------------------------------------------
-- SAMPLE DATA
-- ------------------------------------------------------------
INSERT INTO events (event_id, name, event_date, venue, organizer) VALUES
    (1, 'Tech Fest 2026',      '2026-11-14', 'Main Auditorium', 'Computer Science Dept'),
    (2, 'Annual Sports Meet',  '2026-12-05', 'College Ground',  'Sports Committee'),
    (3, 'Guest Lecture on AI', '2026-11-20', 'Seminar Hall',    'Training and Placement Cell');

INSERT INTO resources (resource_id, name, type, quantity) VALUES
    (1, 'Main Auditorium', 'Hall',       1),
    (2, 'Seminar Hall',    'Hall',       2),
    (3, 'Epson Projector', 'Projector',  4),
    (4, 'Plastic Chairs',  'Chair',    200),
    (5, 'Wireless Mic',    'Microphone', 6);

INSERT INTO bookings (event_id, resource_id, quantity, booking_date) VALUES
    (1, 1,   1, '2026-11-14'),
    (1, 3,   2, '2026-11-14'),
    (1, 4, 120, '2026-11-14'),
    (3, 2,   1, '2026-11-20'),
    (3, 5,   2, '2026-11-20');

-- ------------------------------------------------------------
-- USEFUL QUERIES (for your viva / report)
-- ------------------------------------------------------------
-- Stock and availability of every resource:
--   SELECT * FROM resource_availability;
-- All bookings with names:
--   SELECT * FROM booking_details ORDER BY booking_id;
-- Resources booked per event:
--   SELECT event_name, COUNT(*) AS items, SUM(quantity) AS total_units
--   FROM booking_details GROUP BY event_id, event_name;
-- Events that have no bookings yet:
--   SELECT * FROM events WHERE event_id NOT IN (SELECT event_id FROM bookings);
