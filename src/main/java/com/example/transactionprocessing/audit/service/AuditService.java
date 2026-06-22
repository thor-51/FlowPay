package com.example.transactionprocessing.audit.service;

import com.example.transactionprocessing.audit.entity.AuditLog;
import com.example.transactionprocessing.audit.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Records a single lifecycle event. Metadata serialization failures are swallowed (logged,
     * not thrown): an audit trail gap is preferable to letting an audit-logging bug roll back
     * the actual transaction processing it's supposed to be observing.
     */
    @Transactional
    public void record(UUID transactionId, String eventType, String message, Object metadata) {
        String metadataJson = null;
        if (metadata != null) {
            try {
                metadataJson = objectMapper.writeValueAsString(metadata);
            } catch (JsonProcessingException ex) {
                log.warn("Failed to serialize audit metadata for transaction {} eventType={}: {}",
                        transactionId, eventType, ex.getMessage());
            }
        }

        AuditLog entry = AuditLog.builder()
                .transactionId(transactionId)
                .eventType(eventType)
                .message(message)
                .metadata(metadataJson)
                .build();

        auditLogRepository.save(entry);
    }

    public void record(UUID transactionId, String eventType, String message) {
        record(transactionId, eventType, message, null);
    }
}
