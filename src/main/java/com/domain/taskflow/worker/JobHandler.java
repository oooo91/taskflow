package com.domain.taskflow.worker;

import com.domain.taskflow.domain.Job;

/**
 * 타입별 실행 로직 인터페이스
 */
public interface JobHandler {
    void execute(Job job) throws Exception;
}
