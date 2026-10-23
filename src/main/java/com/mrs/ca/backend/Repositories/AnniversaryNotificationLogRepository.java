package com.mrs.ca.backend.Repositories;

import com.mrs.ca.backend.Models.AnniversaryNotificationLog;
import com.mrs.ca.backend.Models.AnniversaryNotificationStatus;
import com.mrs.ca.backend.Models.AnniversaryNotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnniversaryNotificationLogRepository extends MongoRepository<AnniversaryNotificationLog, String> {

    Optional<AnniversaryNotificationLog> findByClientIdAndAnniversaryYearAndNotificationType(
            String clientId, int anniversaryYear, AnniversaryNotificationType notificationType);

    Optional<AnniversaryNotificationLog> findByMetaMessageId(String metaMessageId);

    List<AnniversaryNotificationLog> findByStatusAndNextRetryAtLessThanEqual(
            AnniversaryNotificationStatus status, LocalDateTime threshold);

    List<AnniversaryNotificationLog> findByAnniversaryDate(LocalDate date);

    List<AnniversaryNotificationLog> findByClientIdOrderByAnniversaryYearDesc(String clientId);

    Page<AnniversaryNotificationLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByClientIdAndAnniversaryYearAndNotificationType(
            String clientId, int anniversaryYear, AnniversaryNotificationType notificationType);
}
