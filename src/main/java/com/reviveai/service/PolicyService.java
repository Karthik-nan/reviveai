package com.reviveai.service;

import com.reviveai.dto.PolicyResponse;

import java.util.List;

public interface PolicyService {

    List<PolicyResponse> getPolicies();
}