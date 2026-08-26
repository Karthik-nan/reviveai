package com.reviveai.repository;

import com.reviveai.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditEventRepository
        extends JpaRepository<AuditEvent, UUID> {
}