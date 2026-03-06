package com.mrs.ca.backend.Config;

import com.mongodb.client.MongoClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Dedicated configuration for MongoDB auditing.
 * @ConditionalOnBean(MongoClient.class) ensures this only activates when
 * a real MongoDB connection is present — so @WebMvcTest slices (which have no
 * MongoClient bean) will safely skip it.
 */
@Configuration
@ConditionalOnBean(MongoClient.class)
@EnableMongoAuditing
public class MongoConfig {
}
