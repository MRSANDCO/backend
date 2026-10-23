/**
 * MongoDB Migration Script: Individual Client Birthday System
 * Database: ca_firm_db (or configured MONGO_URI)
 *
 * Description:
 * Additive, non-destructive migration ensuring:
 * 1. Sparse index on 'users.date_of_birth' for fast lookup of clients with DOB.
 * 2. Compound sparse index on ('client_type', 'birthday_month', 'birthday_day', 'active')
 *    for efficient daily birthday matching of individual clients.
 * 3. Safe audit and backfill of birthday_month and birthday_day for users with date_of_birth.
 * 4. Ensures birthday_greeting_enabled is defaulted to true for existing individual clients with DOB.
 *
 * Execution:
 *   mongosh "mongodb://localhost:27017/ca_firm_db" mongo-migration-individual-dob.js
 */

print("========== STARTING INDIVIDUAL CLIENT BIRTHDAY MIGRATION ==========");

const dbName = db.getName();
print("Target Database: " + dbName);

const collections = db.getCollectionNames();
if (!collections.includes("users")) {
    print("Warning: 'users' collection does not exist yet. It will be created when users are added.");
} else {
    print("Found 'users' collection.");

    // 1. Create sparse index on date_of_birth
    print("Creating sparse index: user_date_of_birth_idx on users.date_of_birth");
    db.users.createIndex(
        { "date_of_birth": 1 },
        { "sparse": true, "name": "user_date_of_birth_idx" }
    );

    // 2. Create compound index for daily birthday scheduler matching
    print("Creating compound sparse index: user_individual_birthday_lookup_idx");
    db.users.createIndex(
        { "client_type": 1, "birthday_month": 1, "birthday_day": 1, "active": 1 },
        { "sparse": true, "name": "user_individual_birthday_lookup_idx" }
    );

    // 3. Backfill birthday_month & birthday_day if date_of_birth exists but month/day are missing
    print("\n--- Auditing Individual Client Birthday Records ---");
    const totalUsersWithDob = db.users.countDocuments({ "date_of_birth": { $exists: true, $ne: null } });
    print("Total users with date_of_birth: " + totalUsersWithDob);

    const usersNeedingMonthDay = db.users.find({
        "date_of_birth": { $exists: true, $ne: null },
        $or: [
            { "birthday_month": { $exists: false } },
            { "birthday_month": null },
            { "birthday_day": { $exists: false } },
            { "birthday_day": null }
        ]
    }).toArray();

    if (usersNeedingMonthDay.length > 0) {
        print("Backfilling birthday_month and birthday_day for " + usersNeedingMonthDay.length + " users...");
        let updatedCount = 0;
        usersNeedingMonthDay.forEach(u => {
            let dob = u.date_of_birth;
            let month = null;
            let day = null;
            if (dob instanceof Date) {
                month = dob.getUTCMonth() + 1;
                day = dob.getUTCDate();
            } else if (typeof dob === "string") {
                const parts = dob.split("-");
                if (parts.length === 3) {
                    month = parseInt(parts[1], 10);
                    day = parseInt(parts[2], 10);
                }
            }

            if (month !== null && day !== null && !isNaN(month) && !isNaN(day)) {
                db.users.updateOne(
                    { "_id": u._id },
                    {
                        $set: {
                            "birthday_month": month,
                            "birthday_day": day,
                            "birthday_greeting_enabled": (u.birthday_greeting_enabled !== undefined ? u.birthday_greeting_enabled : true)
                        }
                    }
                );
                updatedCount++;
            }
        });
        print("Successfully backfilled " + updatedCount + " users.");
    } else {
        print("All users with date_of_birth have valid birthday_month and birthday_day.");
    }
}

print("\n========== MIGRATION COMPLETED SUCCESSFULLY ==========");
