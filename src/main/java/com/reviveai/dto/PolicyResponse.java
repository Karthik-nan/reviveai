package com.reviveai.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PolicyResponse {

    private String errorCode;

    private String strategy;

    private String description;

    private String priorityRule;
}