package com.reviveai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, length = 100)
    private String entityType;

    @Column(nullable = false)
    private UUID entityId;

    @Column(length = 100)
    private String actor;

    @Column(columnDefinition = "TEXT")
    private String eventData;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

