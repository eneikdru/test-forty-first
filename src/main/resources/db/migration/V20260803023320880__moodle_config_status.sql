CREATE TABLE moodle_config_status (
    id VARCHAR(255) PRIMARY KEY,
    last_configured TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    version INT NOT NULL
);

INSERT INTO moodle_config_status (id, last_configured, status, version)
VALUES ('MOODLE_CONFIG', CURRENT_TIMESTAMP, 'PENDING', 1);
