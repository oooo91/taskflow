package com.domain.taskflow.repo;

import com.domain.taskflow.domain.Job;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {
    Optional<Job> findByJobKey(String jobKey);

    /**
     * DB CAS 업데이트 쿼리 추가
     * 리턴값 int가 업데이트된 row 수 반환
     * @param jobId
     * @param workerId
     * @param now
     * @return
     */
    @Modifying
    @Query("""
                update Job j
                    set j.status = com.domain.taskflow.domain.JobStatus.RUNNING, 
                        j.workerId = :workerId, 
                        j.updatedAt = :now
                where j.id = :jobId
                    and j.status = com.domain.taskflow.domain.JobStatus.PENDING 
            """)
    int claimRunning(@Param("jobId") UUID jobId,
                     @Param("workerId") String workerId,
                     @Param("now")OffsetDateTime now);

    /**
     * PENDING 후보 조회 쿼리 (폴링용)
     * @param now
     * @param pageable
     * @return
     */
    @Query("""
        select j from Job j 
        where j.status = com.domain.taskflow.domain.JobStatus.PENDING 
        and (j.scheduledAt is null or j.scheduledAt <= :now) 
        order by j.createdAt asc 
    """)
    List<Job> findRunnablePending(@Param("now") OffsetDateTime now, Pageable pageable);
}
