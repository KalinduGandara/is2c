-- Hibernate does not persist null collection elements, so OFF days need an explicit flag.
ALTER TABLE rotation_pattern_day ADD COLUMN off_day BOOLEAN NOT NULL DEFAULT FALSE;
