ALTER TABLE jobs ADD COLUMN IF NOT EXISTS next_run_at timestamptz;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS max_attempts int NOT NULL DEFAULT 3;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS attempt_count int NOT NULL DEFAULT 0;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS last_error_code varchar(100);
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS last_error_message text;

CREATE INDEX IF NOT EXISTS ix_jobs_retry_wait ON jobs(status, next_run_at);
