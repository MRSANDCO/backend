package com.mrs.ca.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@org.springframework.context.annotation.Bean
	public org.springframework.boot.CommandLineRunner debugMongoUri(
		@org.springframework.beans.factory.annotation.Value("${spring.data.mongodb.uri:NOT_SET}") String mongoUri
	) {
		return args -> {
			// Mask password in log output
			String masked = mongoUri.replaceAll("://([^:]+):([^@]+)@", "://$1:****@");
			System.out.println("========== MONGO DEBUG ==========");
			System.out.println("spring.data.mongodb.uri = " + masked);
			System.out.println("ENV MONGO_URI = " + (System.getenv("MONGO_URI") != null ? "SET" : "NOT SET"));
			System.out.println("ENV MONGO_URL = " + (System.getenv("MONGO_URL") != null ? "SET" : "NOT SET"));
			System.out.println("=================================");
		};
	}
}
