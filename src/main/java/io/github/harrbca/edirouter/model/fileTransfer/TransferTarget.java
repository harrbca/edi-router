package io.github.harrbca.edirouter.model.fileTransfer;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransferTarget {

    private Protocol protocol;
    private String host;
    @Builder.Default private int port = -1;
    private String username;
    private String password;

    private byte[] privateKey;
    private String privateKeyPassphrase;

    private String remoteDirectory;
    private String remoteFilename;

    @Builder.Default private boolean createDirectories = true;
    @Builder.Default private boolean overwrite = true;

    // FTP only
    @Builder.Default private boolean ftpPassiveMode = true;

    // Timeouts
    @Builder.Default private int connectionTimeoutMs = 15000;
    @Builder.Default private int socketTimeoutMs = 30000;

    // SFTP host key verification
    private String sftpHostKeyFingerprint;
    @Builder.Default private boolean sftpTrustUnknownHostKeys = false;

}
