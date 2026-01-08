package com.domain.taskflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public class JobCreateRequest {

    // 멱등키
    public String jobKey;

    @NotBlank
    public String type;

    @NotNull
    public String payload;

    public OffsetDateTime scheduledAt;
}
