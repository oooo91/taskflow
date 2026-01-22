-- Outbox replay/ordering 커서용
ALTER TABLE job_events ADD COLUMN IF NOT EXISTS seq bigserial;

-- publisher 처리 여부(발행 완료 시각)
ALTER TABLE job_events ADD COLUMN IF NOT EXISTS published_at timestamptz;

CREATE INDEX IF NOT EXISTS ix_job_events_seq ON job_events(seq);
CREATE INDEX IF NOT EXISTS ix_job_events_unpublished ON job_events(published_at) WHERE published_at IS NULL;
