-- jobs
create table if not exists jobs (
                                    id uuid primary key,
                                    job_key varchar(200),
    type varchar(50) not null,
    payload jsonb not null,
    status varchar(30) not null,
    scheduled_at timestamptz,
    cancel_requested boolean not null default false,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0
    );

create unique index if not exists ux_jobs_job_key on jobs(job_key);

create index if not exists ix_jobs_status_scheduled on jobs(status, scheduled_at);

-- job_attempts
create table if not exists job_attempts (
                                            id uuid primary key,
                                            job_id uuid not null references jobs(id),
    attempt_no int not null,
    status varchar(30) not null,
    worker_id varchar(100),
    started_at timestamptz not null,
    ended_at timestamptz,
    error_code varchar(100),
    error_message text
    );

create index if not exists ix_attempts_job_id on job_attempts(job_id);

-- outbox events
create table if not exists job_events (
                                          id uuid primary key,
                                          job_id uuid not null references jobs(id),
    event_type varchar(50) not null,
    payload jsonb not null,
    created_at timestamptz not null,
    published_at timestamptz
    );

create index if not exists ix_events_published_at on job_events(published_at, created_at);
