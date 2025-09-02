package io.github.harrbca.edirouter.model.fileTransfer;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UploadResult {
    private boolean success;
    private String protocol;
    private String host;
    private String remotePath;
    private long bytes;
    private long durationMs;
    private String message;
}
