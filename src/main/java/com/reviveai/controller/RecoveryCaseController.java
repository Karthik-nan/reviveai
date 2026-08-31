package com.reviveai.controller;

import com.reviveai.dto.RecoveryCaseResponse;
import com.reviveai.service.RecoveryCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recovery-cases")
@RequiredArgsConstructor
public class RecoveryCaseController {

    private final RecoveryCaseService recoveryCaseService;

    @GetMapping
    public List<RecoveryCaseResponse> getAllRecoveryCases() {

        return recoveryCaseService.getAllRecoveryCases();
    }

    @GetMapping("/{id}")
    public RecoveryCaseResponse getRecoveryCaseById(
            @PathVariable UUID id
    ) {

        return recoveryCaseService.getRecoveryCaseById(id);
    }
}
