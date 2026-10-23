/**
 * MongoDB Migration Script: Automated Company Anniversary & Founder Birthday Greetings
 *
 * IMPORTANT: All field names use snake_case to match the @Field annotations in the
 * Spring Data MongoDB models (e.g., @Field("client_id"), @Field("birthday_month")).
 *
 * Run with mongosh:
 *   mongosh ca_firm_db backend/migrations/mongo-migration-automated-greetings.js
 * Or with mongo shell:
 *   mongo ca_firm_db backend/migrations/mongo-migration-automated-greetings.js
 */

print("===================================================================");
print("Running automated greeting migration for ca_firm_db...");
print("===================================================================");

// 0. Drop any stale camelCase indexes from prior runs (they target nonexistent fields)
print("\n[0/5] Cleaning up stale camelCase indexes from prior migration runs...");
try { db.anniversary_notification_logs.dropIndex("uniq_client_year_type"); print("  -> Dropped stale index: uniq_client_year_type"); } catch (e) { /* does not exist — OK */ }
try { db.anniversary_notification_logs.dropIndex("idx_status_next_retry"); print("  -> Dropped stale index: idx_status_next_retry"); } catch (e) { /* does not exist — OK */ }
try { db.anniversary_leases.dropIndex("uniq_lease_date_type"); print("  -> Dropped stale index: uniq_lease_date_type"); } catch (e) { /* does not exist — OK */ }
try { db.founders.dropIndex("idx_company_active"); print("  -> Dropped stale index: idx_company_active"); } catch (e) { /* does not exist — OK */ }
try { db.founders.dropIndex("idx_birthday_month_day_active"); print("  -> Dropped stale index: idx_birthday_month_day_active"); } catch (e) { /* does not exist — OK */ }
try { db.users.dropIndex("idx_client_formation_active"); print("  -> Dropped stale index: idx_client_formation_active"); } catch (e) { /* does not exist — OK */ }

// 1. anniversary_notification_logs indexes (snake_case field names match @Field annotations)
print("\n[1/5] Creating indexes for anniversary_notification_logs...");
try {
    db.anniversary_notification_logs.createIndex(
        { client_id: 1, anniversary_year: 1, notification_type: 1 },
        { unique: true, name: "client_year_type_unique_idx" }
    );
    print("  -> Created unique index: client_year_type_unique_idx");
} catch (e) {
    print("  -> Index client_year_type_unique_idx already exists or error: " + e.message);
}

try {
    db.anniversary_notification_logs.createIndex(
        { status: 1, next_retry_at: 1 },
        { name: "status_retry_idx" }
    );
    print("  -> Created index: status_retry_idx");
} catch (e) {
    print("  -> Index status_retry_idx already exists or error: " + e.message);
}

// 2. anniversary_leases indexes
print("\n[2/5] Creating indexes for anniversary_leases...");
try {
    db.anniversary_leases.createIndex(
        { lease_date: 1, lease_type: 1 },
        { unique: true, name: "uniq_lease_date_type" }
    );
    print("  -> Created unique index: uniq_lease_date_type");
} catch (e) {
    print("  -> Index uniq_lease_date_type already exists or error: " + e.message);
}

// 3. founders indexes (snake_case: company_client_id, birthday_month, birthday_day)
print("\n[3/5] Creating indexes for founders...");
try {
    db.founders.createIndex(
        { company_client_id: 1, active: 1 },
        { name: "idx_company_active" }
    );
    print("  -> Created index: idx_company_active");
} catch (e) {
    print("  -> Index idx_company_active error: " + e.message);
}

try {
    db.founders.createIndex(
        { birthday_month: 1, birthday_day: 1, active: 1 },
        { name: "idx_birthday_month_day_active" }
    );
    print("  -> Created index: idx_birthday_month_day_active");
} catch (e) {
    print("  -> Index idx_birthday_month_day_active error: " + e.message);
}

// 4. users indexes (snake_case: client_type, formation_month, formation_day)
print("\n[4/5] Creating indexes for users...");
try {
    db.users.createIndex(
        { client_type: 1, formation_month: 1, formation_day: 1, active: 1 },
        { name: "idx_client_formation_active" }
    );
    print("  -> Created index: idx_client_formation_active");
} catch (e) {
    print("  -> Index idx_client_formation_active error: " + e.message);
}

// 5. Backfill formation_month & formation_day in users collection
print("\n[5/5] Backfilling formation_month & formation_day for users...");
let userCursor = db.users.find({ formation_date: { $ne: null } });
let userUpdated = 0;
while (userCursor.hasNext()) {
    let doc = userCursor.next();
    let fDate = doc.formation_date;
    let month = null;
    let day = null;

    if (typeof fDate === 'string' && fDate.includes('-')) {
        let parts = fDate.split('-');
        if (parts.length >= 3) {
            month = parseInt(parts[1], 10);
            day = parseInt(parts[2], 10);
        }
    } else if (fDate instanceof Date) {
        // Date object (UTC or local)
        month = fDate.getUTCMonth() + 1;
        day = fDate.getUTCDate();
    }

    if (month && day && (!doc.formation_month || !doc.formation_day)) {
        db.users.updateOne(
            { _id: doc._id },
            { $set: { formation_month: month, formation_day: day } }
        );
        userUpdated++;
    }
}
print("  -> Backfilled " + userUpdated + " users with formation_month/formation_day.");

// Backfill birthday_month & birthday_day in founders collection
let founderCursor = db.founders.find({ date_of_birth: { $ne: null } });
let founderUpdated = 0;
while (founderCursor.hasNext()) {
    let doc = founderCursor.next();
    let dob = doc.date_of_birth;
    let month = null;
    let day = null;

    if (typeof dob === 'string' && dob.includes('-')) {
        let parts = dob.split('-');
        if (parts.length >= 3) {
            month = parseInt(parts[1], 10);
            day = parseInt(parts[2], 10);
        }
    } else if (dob instanceof Date) {
        month = dob.getUTCMonth() + 1;
        day = dob.getUTCDate();
    }

    if (month && day && (!doc.birthday_month || !doc.birthday_day)) {
        db.founders.updateOne(
            { _id: doc._id },
            { $set: { birthday_month: month, birthday_day: day } }
        );
        founderUpdated++;
    }
}
print("  -> Backfilled " + founderUpdated + " founders with birthday_month/birthday_day.");

print("\n===================================================================");
print("Migration completed successfully.");
print("===================================================================");
