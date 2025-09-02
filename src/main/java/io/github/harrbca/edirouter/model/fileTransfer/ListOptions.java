package io.github.harrbca.edirouter.model.fileTransfer;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ListOptions {
    private String directory;

    @Builder.Default private boolean recursive = false;

    // Simple glob, e.g. "*.edi", "2025-??-*.csv". Null = all files.
    private String glob;

    @Builder.Default private boolean includeDirectories = false;
}
