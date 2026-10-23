/**
 * MongoDB Migration Script: Client Category / Client Type System
 * Database: ca_firm_db (or configured MONGO_URI)
 *
 * Description:
 * Additive, non-destructive migration ensuring:
 * 1. Sparse index on 'users.client_type' for indexed lookups by client category.
 * 2. Compound sparse index on ('client_type', 'active', 'formation_date') for company anniversary filtering.
 * 3. Safe classification audit of existing client records:
 *    - Does NOT blind-update every existing record to COMPANY.
 *    - Does NOT classify clients as INDIVIDUAL merely because company name or formation date is missing.
 *    - Reports unresolved records requiring administrator verification.
 *    - Leaves ambiguous records safely with client_type: null so anniversary greetings are held
 *      until confirmed by an administrator.
 *
 * Execution:
 *   mongosh "mongodb://localhost:27017/ca_firm_db" mongo-migration-client-type.js
 */

print("========== STARTING CLIENT TYPE SYSTEM MIGRATION ==========");

const dbName = db.getName();
print("Target Database: " + dbName);

// 1. Ensure collection 'users' exists
const collections = db.getCollectionNames();
if (!collections.includes("users")) {
    print("Warning: 'users' collection does not exist yet. It will be created when users are added.");
} else {
    print("Found 'users' collection.");

    // 2. Add sparse index on users.client_type
    print("Creating sparse index: user_client_type_idx on users.client_type");
    db.users.createIndex(
        { "client_type": 1 },
        { "sparse": true, "name": "user_client_type_idx" }
    );

    // 3. Add compound sparse index for active company clients with formation dates
    print("Creating compound sparse index: user_company_anniversary_lookup_idx");
    db.users.createIndex(
        { "client_type": 1, "active": 1, "formation_date": 1 },
        { "sparse": true, "name": "user_company_anniversary_lookup_idx" }
    );

    // 4. Safe Classification Audit of Existing Records
    print("\n--- Auditing Existing Client Records ---");
    const totalUsers = db.users.countDocuments();
    const withType = db.users.countDocuments({ "client_type": { $exists: true, $ne: null } });
    const withoutType = db.users.countDocuments({ $or: [{ "client_type": { $exists: false } }, { "client_type": null }] });
    const companyCount = db.users.countDocuments({ "client_type": "COMPANY" });
    const individualCount = db.users.countDocuments({ "client_type": "INDIVIDUAL" });

    print("Total users in database: " + totalUsers);
    print("Users with classified client_type: " + withType + " (COMPANY: " + companyCount + ", INDIVIDUAL: " + individualCount + ")");
    print("Users requiring administrator verification: " + withoutType);

    if (withoutType > 0) {
        print("\nNotice: The following client user IDs have no client_type assigned and will NOT receive anniversary greetings until verified:");
        const unclassified = db.users.find(
            { $or: [{ "client_type": { $exists: false } }, { "client_type": null }] },
            { "user_id": 1, "full_name": 1, "company_name": 1, "formation_date": 1 }
        ).limit(50).toArray();

        unclassified.forEach(u => {
            const hasCompany = u.company_name ? "Yes ('" + u.company_name + "')" : "No";
            const hasDate = u.formation_date ? "Yes (" + u.formation_date + ")" : "No";
            print("  - userId: " + u.user_id + " | Name: " + (u.full_name || "N/A") + " | Company: " + hasCompany + " | FormationDate: " + hasDate);
        });

        if (withoutType > 50) {
            print("  ... and " + (withoutType - 50) + " more unverified records.");
        }

        print("\nADMIN ACTION REQUIRED:");
        print("To classify a client as COMPANY: db.users.updateOne({ user_id: '<userId>' }, { $set: { client_type: 'COMPANY' } })");
        print("To classify a client as INDIVIDUAL: db.users.updateOne({ user_id: '<userId>' }, { $set: { client_type: 'INDIVIDUAL', anniversary_automation_enabled: false } })");
        print("Alternatively, use the Admin Portal API (PUT /api/admin/users/{userId} or PUT /api/admin/anniversary/clients/{userId}/settings).");
    } else {
        print("All client records have been classified.");
    }
}

print("\n========== MIGRATION COMPLETED SUCCESSFULLY ==========");

/**
 * ROLLBACK PROCEDURE (if ever required):
 *
 * To roll back this migration without data loss:
 * 1. Drop the indexes created above:
 *    db.users.dropIndex("user_client_type_idx");
 *    db.users.dropIndex("user_company_anniversary_lookup_idx");
 *
 * 2. Note: Do NOT remove existing data. Any client_type values are non-destructive and backward compatible.
 */
