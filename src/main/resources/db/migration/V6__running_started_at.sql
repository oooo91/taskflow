ALTER TABLE jobs ADD COLUMN IF NOT EXISTS running_started_at timestamptz;

CREATE INDEX IF NOT EXISTS ix_jobs_running_started ON jobs(status, running_started_at);
