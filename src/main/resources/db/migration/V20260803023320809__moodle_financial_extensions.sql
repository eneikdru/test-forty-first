CREATE TABLE financial_document_tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE financial_terms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    term VARCHAR(255) NOT NULL,
    definition TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO financial_document_tags (name, description) VALUES
    ('Budget', 'Tags documents related to budget planning and allocation'),
    ('Invoice', 'Tags invoice documents'),
    ('Contract', 'Tags financial contracts and agreements');

INSERT INTO financial_terms (term, definition) VALUES
    ('ROI', 'Return on Investment'),
    ('EBITDA', 'Earnings Before Interest, Taxes, Depreciation, and Amortization'),
    ('CAPEX', 'Capital Expenditure');
