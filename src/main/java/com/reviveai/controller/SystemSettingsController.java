package com.reviveai.controller;

import com.reviveai.dto.SystemSettingsResponse;
import com.reviveai.service.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SystemSettingsController {

    private final SystemSettingsService systemSettingsService;

    @GetMapping
    public SystemSettingsResponse getSystemSettings() {

        return systemSettingsService.getSystemSettings();
    }
}
