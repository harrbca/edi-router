package io.github.harrbca.edirouter.logging;

import io.github.harrbca.edirouter.model.LogEntry;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.github.harrbca.edirouter.service.LogEventService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class UiLogAppender extends AppenderBase<ILoggingEvent> implements ApplicationContextAware {

    private static ApplicationContext applicationContext;
    private LogEventService logEventService;

    // Patterns to extract structured information from log messages
    private static final Pattern FILE_PROCESSING_PATTERN = Pattern.compile(".*file\\s+([^\\s]+).*table\\s+([A-Z0-9]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILE_DETECTED_PATTERN = Pattern.compile(".*detected.*file[:\\s]+([^\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile(".*file[:\\s]+([^\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile(".*table[:\\s]+([A-Z0-9]+)", Pattern.CASE_INSENSITIVE);

    @Override
    protected void append(ILoggingEvent event) {
        if (logEventService == null && applicationContext != null) {
            try {
                logEventService = applicationContext.getBean(LogEventService.class);
            } catch (Exception e) {
                // Service not available yet, skip this event
                return;
            }
        }

        if (logEventService == null) {
            return; // Service not available yet
        }

        // Only capture logs from our application packages
        String loggerName = event.getLoggerName();
        if (!loggerName.startsWith("com.buckwold.m3datasync")) {
            return;
        }

        LogEntry logEntry = createLogEntry(event);
        logEventService.addLogEntry(logEntry);
    }

    private LogEntry createLogEntry(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        String loggerName = event.getLoggerName();
        
        // Convert Logback level to our LogEntry level
        LogEntry.LogLevel level = convertLogLevel(event.getLevel());
        
        // Determine category based on logger name and message content
        LogEntry.LogCategory category = determineCategory(loggerName, message);
        
        // Extract file and table names from message
        String fileName = extractFileName(message);
        String tableName = extractTableName(message);
        
        return LogEntry.builder()
                .timestamp(LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getTimeStamp()), ZoneId.systemDefault()))
                .level(level)
                .logger(getSimpleLoggerName(loggerName))
                .message(message)
                .fileName(fileName)
                .tableName(tableName)
                .category(category)
                .build();
    }

    private LogEntry.LogLevel convertLogLevel(ch.qos.logback.classic.Level logbackLevel) {
        return switch (logbackLevel.levelInt) {
            case ch.qos.logback.classic.Level.ERROR_INT -> LogEntry.LogLevel.ERROR;
            case ch.qos.logback.classic.Level.WARN_INT -> LogEntry.LogLevel.WARN;
            case ch.qos.logback.classic.Level.INFO_INT -> LogEntry.LogLevel.INFO;
            case ch.qos.logback.classic.Level.DEBUG_INT -> LogEntry.LogLevel.DEBUG;
            case ch.qos.logback.classic.Level.TRACE_INT -> LogEntry.LogLevel.TRACE;
            default -> LogEntry.LogLevel.INFO;
        };
    }

    private LogEntry.LogCategory determineCategory(String loggerName, String message) {
        String lowerMessage = message.toLowerCase();
        
        if (loggerName.contains("FileProcessingService") || 
            lowerMessage.contains("processing file") || 
            lowerMessage.contains("processed file")) {
            return LogEntry.LogCategory.FILE_PROCESSING;
        }
        
        if (loggerName.contains("FileMonitorService") || 
            lowerMessage.contains("detected") || 
            lowerMessage.contains("watcher") ||
            lowerMessage.contains("monitoring")) {
            return LogEntry.LogCategory.FILE_MONITORING;
        }
        
        if (lowerMessage.contains("database") || 
            lowerMessage.contains("sql") || 
            lowerMessage.contains("jdbc")) {
            return LogEntry.LogCategory.DATABASE;
        }
        
        if (lowerMessage.contains("error") || 
            lowerMessage.contains("exception") || 
            lowerMessage.contains("failed")) {
            return LogEntry.LogCategory.ERROR;
        }
        
        return LogEntry.LogCategory.SYSTEM;
    }

    private String extractFileName(String message) {
        // Try file processing pattern first (more specific)
        Matcher matcher = FILE_PROCESSING_PATTERN.matcher(message);
        if (matcher.find()) {
            return extractFileNameFromPath(matcher.group(1));
        }
        
        // Try file detected pattern
        matcher = FILE_DETECTED_PATTERN.matcher(message);
        if (matcher.find()) {
            return extractFileNameFromPath(matcher.group(1));
        }
        
        // Try general file pattern
        matcher = FILE_NAME_PATTERN.matcher(message);
        if (matcher.find()) {
            return extractFileNameFromPath(matcher.group(1));
        }
        
        return null;
    }

    private String extractTableName(String message) {
        // Try file processing pattern first
        Matcher matcher = FILE_PROCESSING_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(2);
        }
        
        // Try general table pattern
        matcher = TABLE_NAME_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }

    private String extractFileNameFromPath(String path) {
        if (path == null) return null;
        
        // Remove path separators and get just the filename
        String fileName = path.replaceAll(".*[/\\\\]", "");
        
        // Clean up any extra characters that might have been captured
        fileName = fileName.replaceAll("[,;:\\s].*", "");
        
        return fileName.isEmpty() ? null : fileName;
    }

    private String getSimpleLoggerName(String fullLoggerName) {
        int lastDot = fullLoggerName.lastIndexOf('.');
        return lastDot >= 0 ? fullLoggerName.substring(lastDot + 1) : fullLoggerName;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        UiLogAppender.applicationContext = applicationContext;
    }
}