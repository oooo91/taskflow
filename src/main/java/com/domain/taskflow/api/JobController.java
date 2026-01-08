package com.domain.taskflow.api;

import com.domain.taskflow.api.dto.JobCreateRequest;
import com.domain.taskflow.api.dto.JobResponse;
import com.domain.taskflow.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping
    public JobResponse create(@Valid @RequestBody JobCreateRequest req) {
        return JobResponse.from(jobService.create(req));
    }

    @GetMapping("/{id}")
    public JobResponse get(@PathVariable(name = "id") UUID id) {
        return JobResponse.from(jobService.get(id));
    }

    @GetMapping
    public List<JobResponse> list() {
        return jobService.list().stream().map(JobResponse::from).toList();
    }

    @PostMapping("/{id}/cancel")
    public JobResponse cancel(@PathVariable(name = "id") UUID id) {
        return JobResponse.from(jobService.cancel(id));
    }
}
