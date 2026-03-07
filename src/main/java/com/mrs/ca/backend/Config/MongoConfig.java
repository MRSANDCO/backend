package com.mrs.ca.backend.Config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@Configuration
@EnableMongoAuditing
public class MongoConfig extends AbstractMongoClientConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MongoConfig.class);

    @Value("${spring.data.mongodb.uri:mongodb://localhost:27017/ca_firm_db}")
    private String mongoUri;

    @Override
    protected String getDatabaseName() {
        ConnectionString cs = new ConnectionString(mongoUri);
        String db = cs.getDatabase();
        return db != null ? db : "ca_firm_db";
    }

    @Override
    public MongoClient mongoClient() {
        log.info("[MONGO] Connecting with URI host: {}",
                mongoUri.replaceAll("://([^:]+):([^@]+)@", "://$1:****@"));
        ConnectionString connectionString = new ConnectionString(mongoUri);
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .build();
        return MongoClients.create(settings);
    }
}
