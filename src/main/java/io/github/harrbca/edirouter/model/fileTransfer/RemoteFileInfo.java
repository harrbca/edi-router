package io.github.harrbca.edirouter.model.fileTransfer;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class RemoteFileInfo {
    private String path;
    private boolean directory;
    private Long sizeBytes;
    private Instant modified;
}
