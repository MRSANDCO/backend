# MRS & CO - Backend API

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![MongoDB](https://img.shields.io/badge/MongoDB-Enabled-green.svg)](https://www.mongodb.com/)
[![Security](https://img.shields.io/badge/Security-Spring_Security_%2B_JWT-blue.svg)](https://spring.io/projects/spring-security)

## Overview

This repository contains the backend service for **MRS & CO**, a Chartered Accountant (CA) firm. It provides a robust, scalable, and secure API to manage client details, documents, user authentication, and an administrative dashboard.

Built with **Java 21** and the **Spring Boot** framework, this project uses **MongoDB** as its primary NoSQL database and implements strong authentication mechanisms using **Spring Security** and **JWT (JSON Web Tokens)**.

> **Security Notice**: This project contains sensitive and proprietary business logic designed specifically for a financial institution (CA Firm). It must remain a **Private Repository** to prevent unauthorized access or disclosure of business operations.

## Features

- **Authentication & Authorization**: Role-based access control (Admin vs. Client) managed securely via JWT.
- **Client Management**: API endpoints to create, fetch, update, and manage clients of the firm.
- **Document Management**: Capabilities for securely uploading, downloading, and associating documents with respective clients.
- **Admin Dashboard Integration**: Specialized administrative endpoints for centralized system overview and control.
- **Rate Limiting**: Built-in rate limiting using `Bucket4j` to prevent abuse and ensure service availability.

## Technology Stack

- **Language**: Java 21
- **Framework**: Spring Boot (v4.0.3)
- **Database**: MongoDB (via `spring-boot-starter-data-mongodb`)
- **Security**: Spring Security + JWT (`jjwt`)
- **Rate Limiting**: Bucket4j (`bucket4j-core`)
- **Build Tool**: Gradle

## Prerequisites

Before running the application, ensure you have the following installed:

- **Java Development Kit (JDK) 21**
- **MongoDB** (Running locally or accessible via a cluster like MongoDB Atlas)
- **Gradle** (Optional, as the Gradle wrapper is included in the project)

## Setup & Run Instructions

### 1. Configure the Environment

You must configure your database connection and provide necessary secrets (like your JWT secret). Update the `application.properties` or `application.yml` located in `src/main/resources/` with your local or production environment variables.

Example configurations needed:
```properties
spring.data.mongodb.uri=mongodb://localhost:27017/mrsandco_db
jwt.secret=YOUR_SUPER_SECRET_KEY
```

### 2. Build the Project

You can compile the project and resolve all dependencies by running the Gradle wrapper:

```bash
# On Windows
gradlew.bat build

# On macOS/Linux
./gradlew build
```

### 3. Run the Application

Start the Spring Boot server using the following command:

```bash
# On Windows
gradlew.bat bootRun

# On macOS/Linux
./gradlew bootRun
```

By default, the application will start on `http://localhost:8080`.

## API Documentation

*(Note: If you have Swagger/OpenAPI configured, add the endpoint here. Typically `http://localhost:8080/swagger-ui.html`)*

For detailed endpoint documentation on Users, Admin interfaces, and Documents, please refer to the respective REST controllers within the source code.

## Testing

To run the unit and integration tests defined in the project:

```bash
# On Windows
gradlew.bat test

# On macOS/Linux
./gradlew test
```

## License

Copyright © MRS & CO. All Rights Reserved.
This code is proprietary and confidential. Unauthorized copying, modification, distribution, or use of this project is strictly prohibited.
