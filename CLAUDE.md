# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot 3.5.5 application called "Life" built with Java 17 and Maven. The project uses:
- Spring Boot Web for REST API development
- MyBatis for database operations (currently disabled via DataSource exclusion)
- Apache POI for Excel file processing
- Lombok for reducing boilerplate code
- JUnit 5 for testing

## Development Commands

### Build and Run
```bash
# Clean and compile the project
./mvnw clean compile

# Run the application
./mvnw spring-boot:run

# Build jar file
./mvnw package

# Clean build artifacts
./mvnw clean
```

### Testing
```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=LifeApplicationTests

# Run tests with coverage
./mvnw test jacoco:report
```

### Development Workflow
```bash
# Install dependencies
./mvnw dependency:resolve

# Check for dependency updates
./mvnw versions:display-dependency-updates

# Format code (if formatter plugin is added)
./mvnw spotless:apply
```

## Architecture

### Package Structure
- `com.sam.life` - Main application package
- Application entry point: `LifeApplication.java` with `@SpringBootApplication`
- Standard Maven directory structure: `src/main/java`, `src/test/java`, `src/main/resources`

### Key Configuration
- **Application Config**: `src/main/resources/application.yaml`
- **Database**: DataSource autoconfiguration is currently excluded, indicating manual database configuration or database-less operation
- **Build Tool**: Maven with Spring Boot parent POM
- **Java Version**: 17

### Dependencies
- **Spring Boot Web**: For REST API endpoints
- **MyBatis**: Database mapping framework (configured but DataSource disabled)
- **Apache POI**: Excel file reading and writing
- **Lombok**: Code generation for getters/setters/constructors
- **Spring Boot Test**: Testing framework with JUnit 5

## API Endpoints

### Excel Processing
- `POST /api/excel/upload-to-drive` - Upload an Excel file, extract required columns, and return a freshly generated Excel file containing:
  - Columns: 記帳時間 (B), 交易金額 (F), 二級分類 (H), 備註 (J)
  - Records sorted by 記帳時間 ascending
  - Sheet title defaults to `記帳資料` and the downloaded file name is always `cost.xlsx`

## Development Notes

- The application currently excludes DataSource autoconfiguration, suggesting either manual database setup or operation without a database
- Lombok annotation processing is properly configured in the Maven compiler plugin
- Static resources and templates directories are present but empty, indicating potential for web UI development
- The UI posts only an Excel file to `/api/excel/upload-to-drive` and immediately downloads the processed workbook returned by the backend as `cost.xlsx`
