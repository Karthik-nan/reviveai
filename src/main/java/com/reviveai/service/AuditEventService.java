package com.reviveai.service;

import com.reviveai.entity.AuditEvent;
import com.reviveai.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditEventService {

    private final AuditEventRepository auditEventRepository;

    public AuditEvent record(
            String eventType,
            String entityType,
            UUID entityId,
            String actor,
            String eventData
    ) {

        AuditEvent auditEvent =
                AuditEvent.builder()
                        .eventType(eventType)
                        .entityType(entityType)
                        .entityId(entityId)
                        .actor(actor)
                        .eventData(eventData)
                        .build();

        return auditEventRepository.save(auditEvent);
    }
}
