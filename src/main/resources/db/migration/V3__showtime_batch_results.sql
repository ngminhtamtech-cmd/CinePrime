CREATE TABLE showtime_batch_results (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 actor_id BIGINT NOT NULL,
 cinema_id BIGINT NOT NULL,
 request_key VARCHAR(36) NOT NULL,
 payload_hash VARCHAR(64) NOT NULL,
 result_json LONGTEXT NOT NULL,
 committed_at DATETIME(6) NOT NULL,
 UNIQUE KEY batch_actor_request(actor_id, request_key),
 FOREIGN KEY(actor_id) REFERENCES users(id),
 FOREIGN KEY(cinema_id) REFERENCES cinemas(id)
);
