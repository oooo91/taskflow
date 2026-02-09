package com.domain.taskflow.service;

import com.domain.taskflow.api.dto.JobCreateRequest;
import com.domain.taskflow.domain.Job;
import com.domain.taskflow.domain.JobEvent;
import com.domain.taskflow.metrics.JobMetrics;
import com.domain.taskflow.repo.JobEventRepository;
import com.domain.taskflow.repo.JobRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final JobEventRepository jobEventRepository;
    private final JobMetrics jobMetrics;

    @Transactional
    public Job create(JobCreateRequest req) {
        // 1) 멱등: jobKey 가 있으면 이미 있는지 먼저 확인
        if (req.jobKey != null && !req.jobKey.isBlank()) {
            Optional<Job> existing = jobRepository.findByJobKey(req.jobKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        // 2) 새 job 생성
        UUID jobId = UUID.randomUUID();
        Job job = new Job(jobId, blankToNull(req.jobKey), req.type, req.payload, req.scheduledAt);
        try {
            jobRepository.save(job);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 jobKey 로 들어온 경우: unique 위반 가능
            if (req.jobKey != null && !req.jobKey.isBlank()) {
                return jobRepository.findByJobKey(req.jobKey)
                        .orElseThrow(() -> e);
            }
            throw e;
        }

        // 3) outbox write (같은 트랜잭션)
        JobEvent ev = new JobEvent(
                UUID.randomUUID(),
                jobId,
                "JOB_CREATED",
                "{\"jobId\":\"" + jobId + "\",\"status\":\"PENDING\"}");
        jobEventRepository.save(ev);

        jobMetrics.incCreated();

        return job;
    }

    @Transactional(readOnly = true)
    public Job get(UUID id) {
        return jobRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("엔티티를 찾을 수 없습니다: " + id));
    }

    @Transactional(readOnly = true)
    public List<Job> list() {
        return jobRepository.findAll();
    }

    public Job cancel(UUID id) {
        Job job = jobRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("엔티티를 찾을 수 없습니다: " + id));
        job.requestCancel();
        jobRepository.save(job);

        JobEvent ev = new JobEvent(
                UUID.randomUUID(),
                id,
                "JOB_CANCEL_REQUESTED",
                "{\"jobId\":\"" + id + "\",\"status\":\"" + job.getStatus() + "\",\"cancelRequested\":" + job.isCancelRequested() + "}"
        );
        jobEventRepository.save(ev);

        return job;
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        if (s.isBlank()) return null;
        return s;
    }
}
