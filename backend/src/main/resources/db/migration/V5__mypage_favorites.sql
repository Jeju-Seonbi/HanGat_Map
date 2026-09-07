CREATE TABLE favorite_places (
     id BIGINT NOT NULL AUTO_INCREMENT,
     user_id BIGINT NOT NULL,
     place_id BIGINT NOT NULL,
     created_at DATETIME(6) NOT NULL,

     PRIMARY KEY (id),

     CONSTRAINT uk_favorite_user_place
         UNIQUE (user_id, place_id),

     KEY idx_favorite_user_created (user_id, created_at, id),
     KEY idx_favorite_place (place_id),

     CONSTRAINT fk_favorite_user
         FOREIGN KEY (user_id)
             REFERENCES users (id)
             ON DELETE CASCADE,

     CONSTRAINT fk_favorite_place
         FOREIGN KEY (place_id)
             REFERENCES places (id)
             ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_reviews_user_status_created
    ON reviews (user_id, status, created_at, id);