package com.domain.taskflow.repo;

import com.domain.taskflow.domain.JobEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JobEventRepository extends JpaRepository<JobEvent, UUID> {
}
