package com.mrs.ca.backend.Services;

import com.mrs.ca.backend.Models.AnniversaryNotificationLog;
import com.mrs.ca.backend.Models.AnniversaryNotificationStatus;
import com.mrs.ca.backend.Repositories.AnniversaryNotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnniversaryStatusReconciliationServiceTest {

    @Mock
    private AnniversaryNotificationLogRepository logRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    private AnniversaryStatusReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService = new AnniversaryStatusReconciliationService(logRepository, mongoTemplate);
    }

    private AnniversaryNotificationLog createLog(String metaMsgId, AnniversaryNotificationStatus status) {
        AnniversaryNotificationLog log = new AnniversaryNotificationLog(
                "client_1", "doc_1", "Alpha Ltd", "Ramesh", "919876543210", 2026, LocalDate.of(2026, 10, 10));
        log.setId("log_123");
        log.setMetaMessageId(metaMsgId);
        log.setStatus(status);
        return log;
    }

    @Test
    void testReconcile_UnknownMessageId_ReturnsFalse() {
        when(logRepository.findByMetaMessageId("unknown_msg_id")).thenReturn(Optional.empty());

        boolean updated = reconciliationService.reconcileStatus("unknown_msg_id", "delivered", 1728556800L, null);

        assertThat(updated).isFalse();
        verify(mongoTemplate, never()).findAndModify(any(), any(), any(), eq(AnniversaryNotificationLog.class));
    }

    @Test
    void testReconcile_AcceptedToDelivered() {
        AnniversaryNotificationLog log = createLog("wamid.123", AnniversaryNotificationStatus.ACCEPTED);
        when(logRepository.findByMetaMessageId("wamid.123")).thenReturn(Optional.of(log));

        boolean updated = reconciliationService.reconcileStatus("wamid.123", "delivered", 1728556800L, null);

        assertThat(updated).isTrue();
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), updateCaptor.capture(), any(FindAndModifyOptions.class), eq(AnniversaryNotificationLog.class));

        Update capturedUpdate = updateCaptor.getValue();
        assertThat(capturedUpdate.getUpdateObject().get("$set").toString()).contains("DELIVERED");
    }

    @Test
    void testReconcile_DeliveredToRead() {
        AnniversaryNotificationLog log = createLog("wamid.123", AnniversaryNotificationStatus.DELIVERED);
        when(logRepository.findByMetaMessageId("wamid.123")).thenReturn(Optional.of(log));

        boolean updated = reconciliationService.reconcileStatus("wamid.123", "read", 1728556900L, null);

        assertThat(updated).isTrue();
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), updateCaptor.capture(), any(FindAndModifyOptions.class), eq(AnniversaryNotificationLog.class));

        Update capturedUpdate = updateCaptor.getValue();
        assertThat(capturedUpdate.getUpdateObject().get("$set").toString()).contains("READ");
    }

    @Test
    void testReconcile_LateDeliveredDoesNotDowngradeRead() {
        AnniversaryNotificationLog log = createLog("wamid.123", AnniversaryNotificationStatus.READ);
        when(logRepository.findByMetaMessageId("wamid.123")).thenReturn(Optional.of(log));

        boolean updated = reconciliationService.reconcileStatus("wamid.123", "delivered", 1728556800L, null);

        assertThat(updated).isTrue();
        // Should not modify database since it is already READ
        verify(mongoTemplate, never()).findAndModify(any(), any(), any(), eq(AnniversaryNotificationLog.class));
    }

    @Test
    void testReconcile_FailedStatusRecorded() {
        AnniversaryNotificationLog log = createLog("wamid.123", AnniversaryNotificationStatus.ACCEPTED);
        when(logRepository.findByMetaMessageId("wamid.123")).thenReturn(Optional.of(log));

        List<Map<String, Object>> errors = List.of(Map.of("code", 131026, "title", "Receiver is incapable of receiving this message"));
        boolean updated = reconciliationService.reconcileStatus("wamid.123", "failed", 1728556800L, errors);

        assertThat(updated).isTrue();
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), updateCaptor.capture(), any(FindAndModifyOptions.class), eq(AnniversaryNotificationLog.class));

        Update capturedUpdate = updateCaptor.getValue();
        assertThat(capturedUpdate.getUpdateObject().get("$set").toString()).contains("FAILED");
        assertThat(capturedUpdate.getUpdateObject().get("$set").toString()).contains("131026");
    }
}
