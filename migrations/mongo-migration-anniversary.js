/**
 * MongoDB Migration Script: Company Formation Anniversary System
 * Database: ca_firm_db (or configured MONGO_URI)
 *
 * Description:
 * Additive, non-destructive migration ensuring:
 * 1. Creation of 'anniversary_notification_logs' collection.
 * 2. Database-enforced compound unique index: (client_id, anniversary_year, notification_type).
 * 3. Lookup indexes for Meta message ID and retry queue.
 * 4. User collection index on 'formation_date'.
 *
 * Execution:
 *   mongosh "mongodb://localhost:27017/ca_firm_db" mongo-migration-anniversary.js
 */

print("========== STARTING ANNIVERSARY SYSTEM MIGRATION ==========");

const dbName = db.getName();
print("Target Database: " + dbName);

// 1. Ensure collection 'anniversary_notification_logs' exists
const collections = db.getCollectionNames();
if (!collections.includes("anniversary_notification_logs")) {
    print("Creating collection: anniversary_notification_logs");
    db.createCollection("anniversary_notification_logs");
} else {
    print("Collection anniversary_notification_logs already exists.");
}

// 2. Add compound unique index on anniversary_notification_logs: (client_id, anniversary_year, notification_type)
print("Creating compound unique index: client_year_type_unique_idx");
db.anniversary_notification_logs.createIndex(
    { "client_id": 1, "anniversary_year": 1, "notification_type": 1 },
    { "unique": true, "name": "client_year_type_unique_idx" }
);

// 3. Add index on meta_message_id for Meta webhook status callbacks
print("Creating sparse index on meta_message_id");
db.anniversary_notification_logs.createIndex(
    { "meta_message_id": 1 },
    { "sparse": true, "name": "meta_message_id_idx" }
);

// 4. Add compound index on status and next_retry_at for retry processor
print("Creating compound index: status_retry_idx");
db.anniversary_notification_logs.createIndex(
    { "status": 1, "next_retry_at": 1 },
    { "name": "status_retry_idx" }
);

// 5. Add index on users.formation_date for query performance
print("Creating sparse index on users.formation_date");
db.users.createIndex(
    { "formation_date": 1 },
    { "sparse": true, "name": "user_formation_date_idx" }
);

print("========== MIGRATION COMPLETED SUCCESSFULLY ==========");

/**
 * ROLLBACK PROCEDURE (if ever required):
 *
 * To roll back this migration without data loss:
 * 1. Drop the indexes created above:
 *    db.anniversary_notification_logs.dropIndex("client_year_type_unique_idx");
 *    db.anniversary_notification_logs.dropIndex("meta_message_id_idx");
 *    db.anniversary_notification_logs.dropIndex("status_retry_idx");
 *    db.users.dropIndex("user_formation_date_idx");
 *
 * 2. Note: Do NOT drop the collection or remove existing fields unless explicitly authorized.
 *    Any newly added fields on 'users' (formation_date, anniversary_automation_enabled, etc.)
 *    are purely additive and do not affect legacy queries.
 */
