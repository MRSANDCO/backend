/**
 * MongoDB Migration Script: Automated Company Anniversary & Founder Birthday Greetings
 *
 * Run with mongosh:
 *   mongosh ca_firm_db backend/migrations/mongo-migration-automated-greetings.js
 * Or with mongo shell:
 *   mongo ca_firm_db backend/migrations/mongo-migration-automated-greetings.js
 */

print("===================================================================");
print("Running automated greeting migration for ca_firm_db...");
print("===================================================================");

// 1. anniversary_notification_logs indexes
print("\n[1/5] Creating indexes for anniversary_notification_logs...");
try {
    db.anniversary_notification_logs.createIndex(
        { clientId: 1, anniversaryYear: 1, notificationType: 1 },
        { unique: true, name: "uniq_client_year_type" }
    );
    print("  -> Created unique index: uniq_client_year_type");
} catch (e) {
    print("  -> Index uniq_client_year_type already exists or error: " + e.message);
}

try {
    db.anniversary_notification_logs.createIndex(
        { status: 1, nextRetryAt: 1 },
        { name: "idx_status_next_retry" }
    );
    print("  -> Created index: idx_status_next_retry");
} catch (e) {
    print("  -> Index idx_status_next_retry already exists or error: " + e.message);
}

// 2. anniversary_leases indexes
print("\n[2/5] Creating indexes for anniversary_leases...");
try {
    db.anniversary_leases.createIndex(
        { leaseDate: 1, leaseType: 1 },
        { unique: true, name: "uniq_lease_date_type" }
    );
    print("  -> Created unique index: uniq_lease_date_type");
} catch (e) {
    print("  -> Index uniq_lease_date_type already exists or error: " + e.message);
}

// 3. founders indexes
print("\n[3/5] Creating indexes for founders...");
try {
    db.founders.createIndex(
        { companyClientId: 1, active: 1 },
        { name: "idx_company_active" }
    );
    print("  -> Created index: idx_company_active");
} catch (e) {
    print("  -> Index idx_company_active error: " + e.message);
}

try {
    db.founders.createIndex(
        { birthdayMonth: 1, birthdayDay: 1, active: 1 },
        { name: "idx_birthday_month_day_active" }
    );
    print("  -> Created index: idx_birthday_month_day_active");
} catch (e) {
    print("  -> Index idx_birthday_month_day_active error: " + e.message);
}

// 4. users indexes
print("\n[4/5] Creating indexes for users...");
try {
    db.users.createIndex(
        { clientType: 1, formationMonth: 1, formationDay: 1, active: 1 },
        { name: "idx_client_formation_active" }
    );
    print("  -> Created index: idx_client_formation_active");
} catch (e) {
    print("  -> Index idx_client_formation_active error: " + e.message);
}

// 5. Backfill formationMonth & formationDay in users collection
print("\n[5/5] Backfilling formationMonth & formationDay for users...");
let userCursor = db.users.find({ formationDate: { $ne: null } });
let userUpdated = 0;
while (userCursor.hasNext()) {
    let doc = userCursor.next();
    let fDate = doc.formationDate;
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

    if (month && day && (!doc.formationMonth || !doc.formationDay)) {
        db.users.updateOne(
            { _id: doc._id },
            { $set: { formationMonth: month, formationDay: day } }
        );
        userUpdated++;
    }
}
print("  -> Backfilled " + userUpdated + " users with formationMonth/formationDay.");

// Backfill birthdayMonth & birthdayDay in founders collection
let founderCursor = db.founders.find({ dateOfBirth: { $ne: null } });
let founderUpdated = 0;
while (founderCursor.hasNext()) {
    let doc = founderCursor.next();
    let dob = doc.dateOfBirth;
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

    if (month && day && (!doc.birthdayMonth || !doc.birthdayDay)) {
        db.founders.updateOne(
            { _id: doc._id },
            { $set: { birthdayMonth: month, birthdayDay: day } }
        );
        founderUpdated++;
    }
}
print("  -> Backfilled " + founderUpdated + " founders with birthdayMonth/birthdayDay.");

print("\n===================================================================");
print("Migration completed successfully.");
print("===================================================================");
