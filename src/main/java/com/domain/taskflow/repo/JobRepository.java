package com.domain.taskflow.repo;

import com.domain.taskflow.domain.Job;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {
    Optional<Job> findByJobKey(String jobKey);
}
