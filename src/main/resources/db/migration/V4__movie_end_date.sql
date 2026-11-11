-- Every movie gets a mandatory release window [release_date, end_date] (Vietnam calendar dates).
-- Showtimes are stored in UTC; their Vietnam date is DATE(CONVERT_TZ(starts_at,'+00:00','+07:00')).
ALTER TABLE movies ADD COLUMN end_date DATE NULL AFTER release_date;

-- Release date: keep it unless an existing, non-cancelled showtime starts earlier; undated movies take
-- their first showtime date, or today when they have none.
UPDATE movies m
LEFT JOIN (SELECT movie_id, MIN(DATE(CONVERT_TZ(starts_at,'+00:00','+07:00'))) first_day, MAX(DATE(CONVERT_TZ(starts_at,'+00:00','+07:00'))) last_day
           FROM showtimes WHERE status<>'CANCELLED' GROUP BY movie_id) s ON s.movie_id=m.id
SET m.release_date = CASE
        WHEN m.release_date IS NULL THEN COALESCE(s.first_day, DATE(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+07:00')))
        WHEN s.first_day IS NOT NULL AND s.first_day < m.release_date THEN s.first_day
        ELSE m.release_date END;

-- End date: 30 days after release, extended to cover the last existing showtime.
UPDATE movies m
LEFT JOIN (SELECT movie_id, MAX(DATE(CONVERT_TZ(starts_at,'+00:00','+07:00'))) last_day
           FROM showtimes WHERE status<>'CANCELLED' GROUP BY movie_id) s ON s.movie_id=m.id
SET m.end_date = GREATEST(DATE_ADD(m.release_date, INTERVAL 30 DAY), COALESCE(s.last_day, m.release_date));

-- "Ngừng chiếu" is replaced by hiding the movie; the stored status no longer drives behaviour.
UPDATE movies SET active = FALSE, status = 'COMING_SOON' WHERE status = 'STOPPED';

ALTER TABLE movies
    MODIFY release_date DATE NOT NULL,
    MODIFY end_date DATE NOT NULL,
    ADD CONSTRAINT movies_release_window CHECK (end_date >= release_date);
