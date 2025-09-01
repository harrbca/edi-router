package io.github.harrbca.edirouter.service;

import io.github.harrbca.edirouter.model.LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Slf4j
@Service
public class LogEventService {

    private static final int MAX_LOG_ENTRIES = 500;
    private final ConcurrentLinkedDeque<LogEntry> logEntries = new ConcurrentLinkedDeque<>();
    private final List<Consumer<LogEntry>> listeners = new CopyOnWriteArrayList<>();

    public void addLogEntry(LogEntry entry) {
        logEntries.addFirst(entry);
        
        // Keep only the most recent entries
        while (logEntries.size() > MAX_LOG_ENTRIES) {
            logEntries.removeLast();
        }

        // Notify all listeners
        listeners.forEach(listener -> {
            try {
                listener.accept(entry);
            } catch (Exception e) {
                log.warn("Error notifying log listener: {}", e.getMessage());
            }
        });
    }

    public List<LogEntry> getRecentLogEntries(int limit) {
        return logEntries.stream()
                .limit(limit)
                .toList();
    }

    public List<LogEntry> getLogEntriesByCategory(LogEntry.LogCategory category, int limit) {
        return logEntries.stream()
                .filter(entry -> entry.getCategory() == category)
                .limit(limit)
                .toList();
    }

    public List<LogEntry> getLogEntriesByLevel(LogEntry.LogLevel level, int limit) {
        return logEntries.stream()
                .filter(entry -> entry.getLevel() == level)
                .limit(limit)
                .toList();
    }

    public void addLogListener(Consumer<LogEntry> listener) {
        listeners.add(listener);
    }

    public void removeLogListener(Consumer<LogEntry> listener) {
        listeners.remove(listener);
    }

    public long getTotalLogCount() {
        return logEntries.size();
    }

    public long getErrorCount() {
        return logEntries.stream()
                .filter(entry -> entry.getLevel() == LogEntry.LogLevel.ERROR)
                .count();
    }

    public long getWarningCount() {
        return logEntries.stream()
                .filter(entry -> entry.getLevel() == LogEntry.LogLevel.WARN)
                .count();
    }

    // Note: Log entries are now automatically captured via UiLogAppender
    // No need for manual convenience methods - just use log.info(), log.error(), etc.
}