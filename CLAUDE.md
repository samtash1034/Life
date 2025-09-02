# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot 3.5.5 application called "Life" built with Java 17 and Maven. The project uses:
- Spring Boot Web for REST API development
- MyBatis for database operations (currently disabled via DataSource exclusion)
- Apache POI for Excel file processing
- Google Drive API for spreadsheet integration
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
- **Google Drive API**: Integration with Google Drive and Google Sheets
- **Lombok**: Code generation for getters/setters/constructors
- **Spring Boot Test**: Testing framework with JUnit 5

## API Endpoints

### Excel Processing
- `POST /api/excel/process` - Upload Excel file and download processed version
  - Extracts columns B (time), F (amount), H (secondary category), J (notes)
  - Sorts by time ascending
  - Returns Excel file with data in columns A, B, C, D

- `POST /api/excel/upload-to-drive` - Upload Excel data to existing Google Drive spreadsheet
  - Parameters: `file` (Excel file), `fileId` (Google Drive file ID)
  - Creates new sheet in existing Google Sheets document
  - Returns success/error message

## Google Drive Setup

1. **建立Google Cloud專案**
   - 到 [Google Cloud Console](https://console.cloud.google.com/)
   - 建立新專案或選擇現有專案
   - 啟用 Google Drive API 和 Google Sheets API

2. **設定OAuth2認證**
   - 前往「APIs & Services」> 「Credentials」
   - 點擊「Create Credentials」> 「OAuth client ID」
   - 選擇「Web application」
   - 下載認證JSON檔案
   - 將檔案內容放到 `src/main/resources/google-credentials.json`

3. **首次執行會開啟瀏覽器進行授權**
   - 系統會在port 8888啟動本地伺服器
   - 瀏覽器會開啟Google授權頁面
   - 完成授權後，認證token會儲存在`tokens`目錄

4. **Google Drive檔案權限**
   - 確保你的Google帳戶有目標檔案的編輯權限
   - 檔案ID可在Google Drive檔案URL中找到

## Development Notes

- The application currently excludes DataSource autoconfiguration, suggesting either manual database setup or operation without a database
- Lombok annotation processing is properly configured in the Maven compiler plugin
- Static resources and templates directories are present but empty, indicating potential for web UI development
- Google Drive integration requires proper authentication setup before use