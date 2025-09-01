# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

EDI Router is a Spring Boot application with Vaadin frontend for routing EDI X12 files from a local directory to FTP/SFTP destinations based on ISA receiver ID. The application monitors directories for file changes and provides a real-time dashboard for tracking processing status.

## Essential Commands

### Building and Running
- `./gradlew.bat bootRun` - Run the Spring Boot application
- `./gradlew.bat build` - Build the complete application (includes frontend)
- `./gradlew.bat clean` - Clean build artifacts
- `./gradlew.bat bootJar` - Create executable JAR

### Frontend Development
- `./gradlew.bat vaadinPrepareFrontend` - Prepare Vaadin frontend dependencies
- `./gradlew.bat vaadinBuildFrontend` - Build frontend bundle with webpack
- `./gradlew.bat vaadinClean` - Clean frontend artifacts and node_modules

### Testing
Note: Tests are currently disabled in build.gradle (line 49: `enabled(false)`)
- `./gradlew.bat test` - Run tests (when enabled)
- `./gradlew.bat check` - Run all verification tasks

## Architecture Overview

### Core Services Architecture
The application follows a service-oriented architecture with event-driven communication:

1. **FileMonitorService** (`src/main/java/io/github/harrbca/edirouter/service/FileMonitorService.java:22`) - Watches the incoming directory using Java NIO WatchService, processes existing files on startup, and handles file events asynchronously.

2. **FileProcessingService** (`src/main/java/io/github/harrbca/edirouter/service/FileProcessingService.java:23`) - Processes individual files by moving them through processing → archive workflow, with error handling that moves failed files to error directory.

3. **LogEventService** (`src/main/java/io/github/harrbca/edirouter/service/LogEventService.java:14`) - Manages in-memory log storage (max 500 entries) and real-time UI updates via listener pattern.

### Event System
- **FileProcessedEvent** - Published when files are processed, includes success status and running totals
- **UiLogAppender** - Custom logback appender that captures application logs and forwards them to LogEventService for real-time UI display

### UI Architecture (Vaadin)
- **MainLayout** - AppLayout with drawer navigation and header
- **DashboardView** - Main view with status cards, real-time activity grid, and event listeners for live updates
- Uses component-based architecture with real-time updates via UI.access() for thread-safe UI modifications

### Configuration
- **FileMonitorProperties** - Configurable file paths (base: C:/M3DataSync), retry settings, and polling intervals
- **AsyncConfig** - Enables Spring's @Async support for background file processing

### Directory Structure
The application manages these directories under the base path:
- `incoming/` - Files to be processed
- `processing/` - Files currently being processed  
- `archive/` - Successfully processed files
- `errors/` - Failed files with error logs

### Key Dependencies
- Spring Boot 3.5.4 with Java 21
- Vaadin 24.8.6 for the web UI
- Lombok for boilerplate reduction
- Logback for logging integration

### Note on Tests
Tests are currently disabled in the Gradle configuration. The EdiRouterApplicationTests.java exists but won't run until tests are re-enabled in build.gradle.