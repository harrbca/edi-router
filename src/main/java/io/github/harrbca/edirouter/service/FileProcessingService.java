package io.github.harrbca.edirouter.service;


import io.github.harrbca.edirouter.config.FileMonitorProperties;
import io.github.harrbca.edirouter.event.FileProcessedEvent;
import io.github.harrbca.edirouter.x12.X12EnvelopeService;
import io.github.harrbca.edirouter.x12.model.TransactionSet;
import io.github.harrbca.edirouter.x12.model.X12ParseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Matcher;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileProcessingService {

    private final FileMonitorProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final X12EnvelopeService x12EnvelopeService;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    private volatile long totalFilesProcessed = 0;


    public boolean processFile(Path sourceFile) {
        String fileName = sourceFile.getFileName().toString();
        
        try {

            log.info("Started processing file {}", fileName);
            Path processingFile = moveToProcessingDirectory(sourceFile);

            // extract the envelope info and log it
            X12ParseResult parseResult = x12EnvelopeService.parse(processingFile);
            String type = parseResult.getFunctionalGroups().stream()
                    .flatMap(g -> g.getTransactionSets().stream())
                    .findFirst()
                    .map(TransactionSet::getTransactionSetIdentifierCode)
                    .orElse("UNKNOWN");
            log.info("Processed file {}, Type: {}, Sender: {}, Receiver: {}", fileName, type, parseResult.getIsa().getInterchangeSenderId(), parseResult.getIsa().getInterchangeReceiverId());

            
            moveToArchiveDirectory(processingFile);
            log.info("Successfully processed file {}", fileName);
            publishFileProcessedEvent(fileName, true);
            return true;

        } catch (Exception e) {
            log.error("Error processing file {}: {}", sourceFile, e.getMessage(), e);
            try {
                moveToErrorDirectory(sourceFile, e.getMessage());
            } catch (Exception moveError) {
                log.error("Failed to move error file {}: {}", sourceFile, moveError.getMessage());
            }
            publishFileProcessedEvent(fileName, false);
            return false;
        }
    }

    private Path moveToProcessingDirectory(Path sourceFile) throws IOException {
        Path targetFile = properties.getProcessingDirectoryPath()
            .resolve(sourceFile.getFileName());
        
        return moveFileWithRetry(sourceFile, targetFile);
    }

    private void moveToArchiveDirectory(Path sourceFile) throws IOException {
        Path tableArchiveDir = properties.getArchiveDirectoryPath();
        Files.createDirectories(tableArchiveDir);
        
        Path targetFile = tableArchiveDir.resolve(sourceFile.getFileName());
        moveFileWithRetry(sourceFile, targetFile);
    }

    private void moveToErrorDirectory(Path sourceFile, String errorReason) throws IOException {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String fileName = sourceFile.getFileName().toString();
        String nameWithoutExt = fileName.contains(".") ? 
            fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        String extension = fileName.contains(".") ? 
            fileName.substring(fileName.lastIndexOf('.')) : "";
        
        Path targetFile = properties.getErrorDirectoryPath()
            .resolve(nameWithoutExt + "_ERROR_" + timestamp + extension);
        
        moveFileWithRetry(sourceFile, targetFile);
        
        // Create error log file
        Path errorLogFile = properties.getErrorDirectoryPath()
            .resolve(nameWithoutExt + "_ERROR_" + timestamp + ".log");
        
        String errorLog = String.format("File: %s%nTimestamp: %s%nError: %s%n", 
            fileName, LocalDateTime.now(), errorReason);
        Files.writeString(errorLogFile, errorLog);
    }

    private Path moveFileWithRetry(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        
        IOException lastException = null;
        for (int attempt = 1; attempt <= properties.getRetryAttempts(); attempt++) {
            try {
                return Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                lastException = e;
                log.warn("Attempt {} to move file {} failed: {}", attempt, source, e.getMessage());
                
                if (attempt < properties.getRetryAttempts()) {
                    try {
                        Thread.sleep(properties.getRetryDelayMs());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("File move interrupted", ie);
                    }
                }
            }
        }
        
        throw new IOException("Failed to move file after " + properties.getRetryAttempts() + " attempts", lastException);
    }
    
    private void publishFileProcessedEvent(String fileName, boolean success) {
        totalFilesProcessed++;
        FileProcessedEvent event = new FileProcessedEvent(this, fileName, success, totalFilesProcessed);
        eventPublisher.publishEvent(event);
    }
}