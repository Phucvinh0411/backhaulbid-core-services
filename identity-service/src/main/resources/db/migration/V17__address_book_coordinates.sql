ALTER TABLE address_book_entries
    ADD COLUMN latitude DOUBLE PRECISION,
    ADD COLUMN longitude DOUBLE PRECISION,
    ADD COLUMN coordinate_source VARCHAR(30),
    ADD COLUMN coordinate_confirmed_at TIMESTAMPTZ;

ALTER TABLE address_book_entries ADD CONSTRAINT address_book_coordinate_pair CHECK (
    (latitude IS NULL AND longitude IS NULL AND coordinate_source IS NULL AND coordinate_confirmed_at IS NULL)
    OR (latitude IS NOT NULL AND longitude IS NOT NULL
        AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180
        AND coordinate_source IS NOT NULL AND coordinate_source = 'USER_CONFIRMED' AND coordinate_confirmed_at IS NOT NULL)
);

-- Rollback (manual): ALTER TABLE address_book_entries DROP CONSTRAINT address_book_coordinate_pair;
-- ALTER TABLE address_book_entries DROP COLUMN latitude, DROP COLUMN longitude,
-- DROP COLUMN coordinate_source, DROP COLUMN coordinate_confirmed_at;
