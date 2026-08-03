-- Manual Rollback Script for V20260803023320490__lms_links_and_analytics.sql
-- Note: Flyway Community Edition does not support automated undo/rollback operations.
-- This file serves as documentation of the manual procedure to revert changes if needed.

DROP TABLE IF EXISTS user_analytics;
DROP TABLE IF EXISTS lms_links;
