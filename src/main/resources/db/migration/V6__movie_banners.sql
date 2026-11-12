-- Every movie still on the website gets a movie banner, appended after existing banners (newest release first).
-- Later movies get theirs automatically when an admin saves or restores them.
INSERT INTO banners(kind, movie_id, title, image, link, sort_order, active)
SELECT 'MOVIE', m.id, LEFT(m.title, 255), NULL, NULL,
       (SELECT COALESCE(MAX(b.sort_order), 0) FROM banners b) + ROW_NUMBER() OVER (ORDER BY m.release_date DESC, m.id DESC),
       TRUE
FROM movies m
WHERE m.active = TRUE
  AND m.end_date >= CURRENT_DATE
  AND NOT EXISTS (SELECT 1 FROM banners x WHERE x.movie_id = m.id);
