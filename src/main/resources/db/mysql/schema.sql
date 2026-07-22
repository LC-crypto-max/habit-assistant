CREATE TABLE IF NOT EXISTS agent_query_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id VARCHAR(80) NOT NULL,
    user_id VARCHAR(80) NOT NULL,
    adapter VARCHAR(80) NOT NULL,
    platform VARCHAR(80) NOT NULL,
    intent VARCHAR(80) NOT NULL,
    url VARCHAR(1024) NULL,
    query VARCHAR(512) NULL,
    prompt VARCHAR(3000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    claimed_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    error_message VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_query_task_task_id (task_id),
    KEY idx_agent_query_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_activity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(120) NOT NULL,
    type VARCHAR(32) NOT NULL,
    platform VARCHAR(120) NULL,
    title VARCHAR(512) NULL,
    url VARCHAR(1024) NULL,
    text TEXT NULL,
    occurred_at DATETIME(6) NOT NULL,
    confidence VARCHAR(160) NULL,
    data_level VARCHAR(160) NULL,
    source VARCHAR(160) NULL,
    detection_reason VARCHAR(160) NULL,
    matched_keyword VARCHAR(160) NULL,
    raw_evidence MEDIUMTEXT NULL,
    PRIMARY KEY (id),
    KEY idx_user_activity_user_time (user_id, occurred_at),
    KEY idx_user_activity_platform_time (platform, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_activity_tags (
    user_activity_id BIGINT NOT NULL,
    tags VARCHAR(80) NULL,
    KEY idx_user_activity_tags_activity (user_activity_id),
    CONSTRAINT fk_user_activity_tags_activity
        FOREIGN KEY (user_activity_id) REFERENCES user_activity (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS interest_term (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(120) NOT NULL,
    term VARCHAR(255) NOT NULL,
    weight DOUBLE NOT NULL,
    hit_count INT NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_interest_user_term (user_id, term)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS content_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    platform VARCHAR(80) NULL,
    external_id VARCHAR(200) NULL,
    title VARCHAR(512) NULL,
    url VARCHAR(2048) NULL,
    author VARCHAR(300) NULL,
    summary TEXT NULL,
    published_at DATETIME(6) NULL,
    collected_at DATETIME(6) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_content_item_hash (content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS content_item_tags (
    content_item_id BIGINT NOT NULL,
    tags VARCHAR(120) NULL,
    KEY idx_content_item_tags_content (content_item_id),
    CONSTRAINT fk_content_item_tags_content
        FOREIGN KEY (content_item_id) REFERENCES content_item (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS recommendation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(120) NOT NULL,
    content_item_id BIGINT NOT NULL,
    recommendation_date DATE NOT NULL,
    score DOUBLE NOT NULL,
    reason VARCHAR(2000) NULL,
    feedback VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_recommendation_user_date (user_id, recommendation_date),
    KEY idx_recommendation_content (content_item_id),
    CONSTRAINT fk_recommendation_content
        FOREIGN KEY (content_item_id) REFERENCES content_item (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS habit_submission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nickname VARCHAR(255) NULL,
    habit_name VARCHAR(255) NULL,
    content TEXT NULL,
    record_date DATE NULL,
    remark TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS daily_interest_profile (
    profile_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NULL,
    profile_date DATE NULL,
    profile_json VARCHAR(4000) NULL,
    PRIMARY KEY (profile_id),
    KEY idx_daily_interest_user_date (user_id, profile_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS daily_recommendation (
    recommendation_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NULL,
    recommendation_date DATE NULL,
    recommendation_json VARCHAR(4000) NULL,
    PRIMARY KEY (recommendation_id),
    KEY idx_daily_recommendation_user_date (user_id, recommendation_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS interest_event (
    event_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NULL,
    source VARCHAR(255) NULL,
    platform VARCHAR(255) NULL,
    event_type VARCHAR(255) NULL,
    title VARCHAR(512) NULL,
    url VARCHAR(1024) NULL,
    author VARCHAR(255) NULL,
    summary VARCHAR(1000) NULL,
    timestamp DATETIME(6) NULL,
    weight DOUBLE NOT NULL,
    PRIMARY KEY (event_id),
    KEY idx_interest_event_user_time (user_id, timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS interest_event_tags (
    interest_event_event_id VARCHAR(255) NOT NULL,
    tags VARCHAR(255) NULL,
    KEY idx_interest_event_tags_event (interest_event_event_id),
    CONSTRAINT fk_interest_event_tags_event
        FOREIGN KEY (interest_event_event_id) REFERENCES interest_event (event_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_data_authorization (
    authorization_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NULL,
    sanitize BOOLEAN NOT NULL,
    keep_raw_text BOOLEAN NOT NULL,
    allow_sensitive_data BOOLEAN NOT NULL,
    revoked BOOLEAN NOT NULL,
    expire_at DATETIME(6) NULL,
    created_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (authorization_id),
    KEY idx_authorization_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_data_authorization_granted_scopes (
    user_data_authorization_authorization_id VARCHAR(255) NOT NULL,
    granted_scopes VARCHAR(64) NULL,
    KEY idx_authorization_scopes_auth (user_data_authorization_authorization_id),
    CONSTRAINT fk_authorization_scopes_auth
        FOREIGN KEY (user_data_authorization_authorization_id)
        REFERENCES user_data_authorization (authorization_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_data_authorization_allowed_apps (
    user_data_authorization_authorization_id VARCHAR(255) NOT NULL,
    allowed_apps VARCHAR(255) NULL,
    KEY idx_authorization_apps_auth (user_data_authorization_authorization_id),
    CONSTRAINT fk_authorization_apps_auth
        FOREIGN KEY (user_data_authorization_authorization_id)
        REFERENCES user_data_authorization (authorization_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_data_authorization_allowed_domains (
    user_data_authorization_authorization_id VARCHAR(255) NOT NULL,
    allowed_domains VARCHAR(255) NULL,
    KEY idx_authorization_domains_auth (user_data_authorization_authorization_id),
    CONSTRAINT fk_authorization_domains_auth
        FOREIGN KEY (user_data_authorization_authorization_id)
        REFERENCES user_data_authorization (authorization_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_data_authorization_allowed_paths (
    user_data_authorization_authorization_id VARCHAR(255) NOT NULL,
    allowed_paths VARCHAR(255) NULL,
    KEY idx_authorization_paths_auth (user_data_authorization_authorization_id),
    CONSTRAINT fk_authorization_paths_auth
        FOREIGN KEY (user_data_authorization_authorization_id)
        REFERENCES user_data_authorization (authorization_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Idempotent repair for databases created by the earlier 255-character schema.
ALTER TABLE content_item MODIFY COLUMN summary TEXT NULL;
ALTER TABLE content_item MODIFY COLUMN title VARCHAR(512) NULL;
ALTER TABLE content_item MODIFY COLUMN url VARCHAR(2048) NULL;
ALTER TABLE recommendation MODIFY COLUMN reason VARCHAR(2000) NULL;
