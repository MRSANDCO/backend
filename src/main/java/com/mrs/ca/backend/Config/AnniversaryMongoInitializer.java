package com.mrs.ca.backend.Config;

import com.mrs.ca.backend.Models.AnniversaryNotificationLog;
import com.mrs.ca.backend.Models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * Initializes and verifies essential MongoDB indexes for anniversary automation idempotency on application startup.
 */
@Component
public class AnniversaryMongoInitializer {

    private static final Logger log = LoggerFactory.getLogger(AnniversaryMongoInitializer.class);

    private final MongoTemplate mongoTemplate;

    public AnniversaryMongoInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        try {
            log.info("[ANNIVERSARY-INIT] Verifying MongoDB indexes for anniversary notification logs...");

            // 1. Database-enforced compound unique idempotency constraint: (client_id, anniversary_year, notification_type)
            mongoTemplate.indexOps(AnniversaryNotificationLog.class).ensureIndex(
                    new Index()
                            .on("client_id", Sort.Direction.ASC)
                            .on("anniversary_year", Sort.Direction.ASC)
                            .on("notification_type", Sort.Direction.ASC)
                            .unique()
                            .named("client_year_type_unique_idx")
            );

            // 2. Index on Meta WhatsApp Message ID for webhook lookup
            mongoTemplate.indexOps(AnniversaryNotificationLog.class).ensureIndex(
                    new Index()
                            .on("meta_message_id", Sort.Direction.ASC)
                            .sparse()
                            .named("meta_message_id_idx")
            );

            // 3. Compound index on (status, next_retry_at) for retry worker query
            mongoTemplate.indexOps(AnniversaryNotificationLog.class).ensureIndex(
                    new Index()
                            .on("status", Sort.Direction.ASC)
                            .on("next_retry_at", Sort.Direction.ASC)
                            .named("status_retry_idx")
            );

            // 4. Index on User formation_date for performance
            mongoTemplate.indexOps(User.class).ensureIndex(
                    new Index()
                            .on("formation_date", Sort.Direction.ASC)
                            .sparse()
                            .named("user_formation_date_idx")
            );

            // 5. Sparse index on User client_type for category filtering
            mongoTemplate.indexOps(User.class).ensureIndex(
                    new Index()
                            .on("client_type", Sort.Direction.ASC)
                            .sparse()
                            .named("user_client_type_idx")
            );

            // 6. Compound sparse index on (client_type, active, formation_date) for anniversary lookup
            mongoTemplate.indexOps(User.class).ensureIndex(
                    new Index()
                            .on("client_type", Sort.Direction.ASC)
                            .on("active", Sort.Direction.ASC)
                            .on("formation_date", Sort.Direction.ASC)
                            .sparse()
                            .named("user_company_anniversary_lookup_idx")
            );

            // 7. Sparse index on User date_of_birth for individual clients
            mongoTemplate.indexOps(User.class).ensureIndex(
                    new Index()
                            .on("date_of_birth", Sort.Direction.ASC)
                            .sparse()
                            .named("user_date_of_birth_idx")
            );

            // 8. Compound sparse index on (client_type, birthday_month, birthday_day, active) for birthday scheduler
            mongoTemplate.indexOps(User.class).ensureIndex(
                    new Index()
                            .on("client_type", Sort.Direction.ASC)
                            .on("birthday_month", Sort.Direction.ASC)
                            .on("birthday_day", Sort.Direction.ASC)
                            .on("active", Sort.Direction.ASC)
                            .sparse()
                            .named("user_individual_birthday_lookup_idx")
            );

            log.info("[ANNIVERSARY-INIT] All anniversary, birthday, and client category MongoDB indexes successfully verified and ensured.");
        } catch (Exception e) {
            log.warn("[ANNIVERSARY-INIT] Warning while ensuring anniversary indexes: {}", e.getMessage());
        }
    }
}
