-- Create table for document comments
CREATE TABLE document_comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    username VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    user_role VARCHAR(100),
    text VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_document_comments_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
);

-- Create table for document actualization requests
CREATE TABLE document_actualization_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    requester_id VARCHAR(100) NOT NULL,
    requester_username VARCHAR(255) NOT NULL,
    requester_full_name VARCHAR(255),
    requester_role VARCHAR(100),
    reason VARCHAR(4000) NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_document_actualization_requests_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
);
