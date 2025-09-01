package io.github.harrbca.edirouter.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class LogEntry {
    private LocalDateTime timestamp;
    private LogLevel level;
    private String logger;
    private String message;
    private String fileName;
    private String tableName;
    private LogCategory category;

    public enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR
    }

    public enum LogCategory {
        FILE_PROCESSING,
        FILE_MONITORING,
        DATABASE,
        SYSTEM,
        ERROR
    }

    public String getFormattedMessage() {
        if (fileName != null && tableName != null) {
            return String.format("[%s] %s", tableName, message);
        } else if (fileName != null) {
            return String.format("[File: %s] %s", fileName, message);
        }
        return message;
    }

    public String getLevelIcon() {
        return switch (level) {
            case ERROR -> "❌";
            case WARN -> "⚠️";
            case INFO -> "ℹ️";
            case DEBUG -> "🐛";
            case TRACE -> "🔍";
        };
    }

    public String getCategoryIcon() {
        return switch (category) {
            case FILE_PROCESSING -> "📄";
            case FILE_MONITORING -> "👁️";
            case DATABASE -> "🗄️";
            case SYSTEM -> "⚙️";
            case ERROR -> "❌";
        };
    }
}