package io.github.harrbca.edirouter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

@Data
@Component
@ConfigurationProperties(prefix = "app.file-monitor")
public class FileMonitorProperties {

    private String baseDirectory = "C:/M3DataSync";
    private String incomingDirectory = "incoming";
    private String archiveDirectory = "archive";
    private String errorDirectory = "errors";
    private String processingDirectory = "processing";
    private long pollIntervalMs = 5000;
    private int retryAttempts = 3;
    private long retryDelayMs = 1000;

    public Path getBaseDirectoryPath() {
        return Paths.get(baseDirectory);
    }

    public Path getIncomingDirectoryPath() {
        return getBaseDirectoryPath().resolve(incomingDirectory);
    }

    public Path getArchiveDirectoryPath() {
        return getBaseDirectoryPath().resolve(archiveDirectory);
    }

    public Path getErrorDirectoryPath() {
        return getBaseDirectoryPath().resolve(errorDirectory);
    }

    public Path getProcessingDirectoryPath() {
        return getBaseDirectoryPath().resolve(processingDirectory);
    }


}