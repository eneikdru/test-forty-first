-- Restart the identity sequence for documents table so H2 doesn't conflict with manually seeded documents 1-6
ALTER TABLE documents ALTER COLUMN id RESTART WITH 100;
