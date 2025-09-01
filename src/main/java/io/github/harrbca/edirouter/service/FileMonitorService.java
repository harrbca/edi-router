package io.github.harrbca.edirouter.service;

import io.github.harrbca.edirouter.config.FileMonitorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static java.nio.file.StandardWatchEventKinds.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileMonitorService {

    private final FileMonitorProperties properties;
    private final FileProcessingService fileProcessingService;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private WatchService watchService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application ready - initializing file monitoring system");
        initializeDirectories();
        processExistingFiles();
        startDirectoryWatcher();
    }

    private void initializeDirectories() {
        try {
            log.info("Creating directory structure under: {}", properties.getBaseDirectoryPath());
            
            Files.createDirectories(properties.getIncomingDirectoryPath());
            Files.createDirectories(properties.getArchiveDirectoryPath());
            Files.createDirectories(properties.getErrorDirectoryPath());
            Files.createDirectories(properties.getProcessingDirectoryPath());
            
            log.info("Directory structure initialized successfully");
            log.info("  Incoming: {}", properties.getIncomingDirectoryPath());
            log.info("  Archive: {}", properties.getArchiveDirectoryPath());
            log.info("  Errors: {}", properties.getErrorDirectoryPath());
            log.info("  Processing: {}", properties.getProcessingDirectoryPath());
            
        } catch (IOException e) {
            log.error("Failed to create directory structure: {}", e.getMessage(), e);
            throw new RuntimeException("Cannot initialize file monitoring directories", e);
        }
    }

    private void processExistingFiles() {
        try {
            Path incomingDir = properties.getIncomingDirectoryPath();
            log.info("Scanning for existing files in: {}", incomingDir);
            
            if (!Files.exists(incomingDir)) {
                log.info("Incoming directory does not exist yet: {}", incomingDir);
                return;
            }

            try (Stream<Path> files = Files.list(incomingDir)) {
                long fileCount = files
                    .filter(Files::isRegularFile)
                    .peek(file -> log.info("Processing existing file: {}", file.getFileName()))
                    .mapToLong(file -> fileProcessingService.processFile(file) ? 1 : 0)
                    .sum();
                
                log.info("Processed {} existing files on startup", fileCount);
            }
            
        } catch (IOException e) {
            log.error("Error processing existing files: {}", e.getMessage(), e);
        }
    }

    @Async
    public CompletableFuture<Void> startDirectoryWatcher() {
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("File monitor is already running");
            return CompletableFuture.completedFuture(null);
        }

        try {
            watchService = FileSystems.getDefault().newWatchService();
            Path incomingDir = properties.getIncomingDirectoryPath();
            
            incomingDir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY);
            log.info("Started directory watcher for: {}", incomingDir);
            
            while (isRunning.get()) {
                try {
                    WatchKey key = watchService.take();
                    
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        
                        if (kind == OVERFLOW) {
                            log.warn("Directory watch overflow - some events may have been lost");
                            continue;
                        }
                        
                        @SuppressWarnings("unchecked")
                        WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                        Path fileName = pathEvent.context();
                        Path fullPath = incomingDir.resolve(fileName);
                        
                        if (kind == ENTRY_CREATE || kind == ENTRY_MODIFY) {
                            handleFileEvent(fullPath);
                        }
                    }
                    
                    boolean valid = key.reset();
                    if (!valid) {
                        log.error("Directory watch key is no longer valid - stopping watcher");
                        break;
                    }
                    
                } catch (InterruptedException e) {
                    log.info("File monitor interrupted - stopping");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error in file monitor loop: {}", e.getMessage(), e);
                    try {
                        Thread.sleep(properties.getRetryDelayMs());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
        } catch (IOException e) {
            log.error("Failed to start directory watcher: {}", e.getMessage(), e);
        } finally {
            cleanup();
        }
        
        return CompletableFuture.completedFuture(null);
    }

    private void handleFileEvent(Path filePath) {
        String fileName = filePath.getFileName().toString();

        // Wait a bit to ensure file is completely written (Windows file locking)
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        // Check if file exists and is readable
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            log.debug("File no longer exists or is not readable: {}", filePath);
            return;
        }

        log.info("Detected new/modified file: {}", fileName);
        
        // Process the file asynchronously to avoid blocking the watcher
        CompletableFuture.runAsync(() -> {
            try {
                fileProcessingService.processFile(filePath);
            } catch (Exception e) {
                log.error("Error processing file {}: {}", filePath, e.getMessage(), e);
            }
        });
    }

    public void stopMonitoring() {
        log.info("Stopping file monitor...");
        isRunning.set(false);
        cleanup();
    }

    private void cleanup() {
        if (watchService != null) {
            try {
                watchService.close();
                log.info("File monitor stopped");
            } catch (IOException e) {
                log.error("Error closing watch service: {}", e.getMessage());
            }
        }
        isRunning.set(false);
    }

    public boolean isRunning() {
        return isRunning.get();
    }
}