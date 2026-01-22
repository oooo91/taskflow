package com.domain.taskflow.repo;

import com.domain.taskflow.domain.JobEvent;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JobEventRepository extends JpaRepository<JobEvent, UUID> {

    // replay: LastSeq 이후 이벤트를 seq 순서대로 가져오기
    @Query("""
            select e from JobEvent e where e.seq > :lastSeq order by e.seq asc
            """)
    List<JobEvent> findAfterSeq(
            @Param("lastSeq") long lastSeq, PageRequest pageRequest);
}
