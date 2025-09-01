package io.github.harrbca.edirouter.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class FileProcessedEvent extends ApplicationEvent {
    
    private final String fileName;
    private final boolean success;
    private final long totalFilesProcessed;
    
    public FileProcessedEvent(Object source, String fileName, boolean success, long totalFilesProcessed) {
        super(source);
        this.fileName = fileName;
        this.success = success;
        this.totalFilesProcessed = totalFilesProcessed;
    }
}