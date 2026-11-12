-- Home hero banners managed by admins, plus a small settings table for the rotation interval.
CREATE TABLE banners (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  kind VARCHAR(255) NOT NULL,
  movie_id BIGINT,
  title VARCHAR(255) NOT NULL,
  image VARCHAR(255),
  link VARCHAR(255),
  sort_order INT NOT NULL,
  active BOOLEAN NOT NULL,
  starts_at DATETIME(6),
  ends_at DATETIME(6),
  FOREIGN KEY(movie_id) REFERENCES movies(id),
  INDEX banners_display(active, sort_order),
  CONSTRAINT banners_kind CHECK (kind IN ('MOVIE','PROMO')),
  CONSTRAINT banners_target CHECK ((kind='MOVIE' AND movie_id IS NOT NULL) OR (kind='PROMO' AND movie_id IS NULL)),
  CONSTRAINT banners_window CHECK (starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at)
);

CREATE TABLE settings (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL UNIQUE,
  value VARCHAR(255) NOT NULL
);
INSERT INTO settings(name, value) VALUES ('banner.interval', '6');

-- Old BANNER contents become promotion banners (active only when they have an image), then retire from contents.
INSERT INTO banners(kind, movie_id, title, image, link, sort_order, active)
SELECT 'PROMO', NULL, title, NULLIF(image, ''), NULLIF(link, ''), ROW_NUMBER() OVER (ORDER BY id), active AND image IS NOT NULL AND image <> ''
FROM contents WHERE kind = 'BANNER';
UPDATE contents SET active = FALSE WHERE kind = 'BANNER';

-- Drop the generated placeholder illustrations from articles/offers and movie banners; real uploads stay untouched.
UPDATE contents SET image = NULL WHERE image LIKE '/images/poster-%';
UPDATE movies SET banner = NULL WHERE banner LIKE '/images/poster-%';
