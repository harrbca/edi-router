package io.github.harrbca.edirouter.service;

import io.github.harrbca.edirouter.model.fileTransfer.*;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import net.schmizz.sshj.xfer.FileSystemFile;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class FileTransferService {

    public UploadResult upload(Path localFile, TransferTarget target) {
        long start = System.currentTimeMillis();
        String defaultName = localFile.getFileName().toString();
        String remoteName = (target.getRemoteFilename() == null || target.getRemoteFilename().isBlank())
                ? defaultName
                : target.getRemoteFilename();

        try {
            if(target.getProtocol() == Protocol.FTP) {
                return doFtpUpload(localFile, target, remoteName, start);
            } else if (target.getProtocol() == Protocol.SFTP) {
                return doSftpUpload(localFile, target, remoteName, start);
            } else {
                return UploadResult.builder()
                        .success(false)
                        .protocol(String.valueOf(target.getProtocol()))
                        .host(target.getHost())
                        .remotePath(composeRemotePath(target.getRemoteDirectory(), remoteName))
                        .message("Unsupported protocol: " + target.getProtocol())
                        .durationMs(System.currentTimeMillis() - start)
                        .build();
            }
        } catch (Exception ex) {
            log.error("Upload failed: file = {}, type= {}, host = {}, user = {}",
                    localFile, target.getProtocol(), target.getHost(), target.getUsername(), ex);
            return UploadResult.builder()
                    .success(false)
                    .protocol(String.valueOf(target.getProtocol()))
                    .host(target.getHost())
                    .remotePath(composeRemotePath(target.getRemoteDirectory(), remoteName))
                    .message(ex.getMessage())
                    .durationMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    public List<RemoteFileInfo> listDirectory(TransferTarget target) throws Exception {
        return listDirectory(target, ListOptions.builder().build());
    }

    public List<RemoteFileInfo> listDirectory(TransferTarget target, ListOptions options) throws Exception {
        String dir = normalizedDir(firstNonBlank(options.getDirectory(), target.getRemoteDirectory(), "/"));
        if(target.getProtocol() == Protocol.FTP) {
            return listFtp(target, dir, options);
        } else if (target.getProtocol() == Protocol.SFTP) {
            return listSftp(target, dir, options);
        } else {
            throw new IllegalArgumentException("Unsupported protocol: " + target.getProtocol());
        }
    }

    private UploadResult doFtpUpload(Path localFile, TransferTarget target, String remoteName, long start) throws Exception {
        int port = (target.getPort() > 0) ? target.getPort() : 21;
        FTPClient ftp = new FTPClient();
        ftp.setConnectTimeout(target.getConnectionTimeoutMs());
        ftp.setDefaultTimeout(target.getSocketTimeoutMs());

        try {
            log.info("Connecting (FTP) to {}:{}", target.getHost(), port);
            ftp.connect(target.getHost(), port);
            boolean logged = ftp.login(target.getUsername(), target.getPassword());
            if (!logged) {
                throw new IllegalStateException("FTP login failed for user " + target.getUsername());
            }

            if (target.isFtpPassiveMode()) {
                ftp.enterLocalPassiveMode();
            }

            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            ftp.setSoTimeout(target.getSocketTimeoutMs());

            if (target.isCreateDirectories() && notBlank(target.getRemoteDirectory())) {
                ensureFtpDirectories(ftp, normalizedDir(target.getRemoteDirectory()));
            }

            if (notBlank(target.getRemoteDirectory())) {
                boolean cwdOk = ftp.changeWorkingDirectory(normalizedDir(target.getRemoteDirectory()));
                if (!cwdOk) throw new IllegalStateException("Could not change directory to " + target.getRemoteDirectory());
            }

            long bytes = Files.size(localFile);
            String remotePath = composeRemotePath(target.getRemoteDirectory(), remoteName);

            if (!target.isOverwrite()) {
                FTPFile[] existing = ftp.listFiles(remoteName);
                if (existing != null && existing.length > 0 && existing[0].isFile()) {
                    throw new IllegalStateException("Remote file exists and overwrite=false: " + remotePath);
                }
            }

            try (InputStream in = new BufferedInputStream(new FileInputStream(localFile.toFile()))) {
                boolean ok = ftp.storeFile(remoteName, in);
                if (!ok) throw new IllegalStateException("FTP storeFile returned false for " + remotePath);
            }

            log.info("Processed file {}, Type: {}, Sender: {}, Receiver: {}",
                    localFile.getFileName(), "FTP", target.getUsername(), target.getHost());

            return UploadResult.builder()
                    .success(true)
                    .protocol("FTP")
                    .host(target.getHost())
                    .remotePath(remotePath)
                    .bytes(bytes)
                    .durationMs(System.currentTimeMillis() - start)
                    .message("OK @ " + Instant.now())
                    .build();
        } finally {
            if (ftp.isConnected()) {
                try { ftp.logout(); } catch (Exception ignore) {}
                try { ftp.disconnect(); } catch (Exception ignore) {}
            }
        }
    }

    private List<RemoteFileInfo> listFtp(TransferTarget t, String dir, ListOptions opts) throws Exception {
        int port = (t.getPort() > 0) ? t.getPort() : 21;
        FTPClient ftp = new FTPClient();
        List<RemoteFileInfo> out = new ArrayList<>();
        Pattern pattern = globToPattern(opts.getGlob());

        try {
            ftp.setConnectTimeout(t.getConnectionTimeoutMs());
            ftp.setDefaultTimeout(t.getSocketTimeoutMs());
            ftp.connect(t.getHost(), port);
            if (!ftp.login(t.getUsername(), t.getPassword())) {
                throw new IllegalStateException("FTP login failed for user " + t.getUsername());
            }
            if (t.isFtpPassiveMode()) ftp.enterLocalPassiveMode();
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            ftp.setSoTimeout(t.getSocketTimeoutMs());

            walkFtp(ftp, dir, opts.isRecursive(), pattern, opts.isIncludeDirectories(), out);
            return out;
        } finally {
            if (ftp.isConnected()) {
                try { ftp.logout(); } catch (Exception ignore) {}
                try { ftp.disconnect(); } catch (Exception ignore) {}
            }
        }
    }

    private void walkFtp(FTPClient ftp, String currentDir, boolean recursive, Pattern pattern,
                         boolean includeDirs, List<RemoteFileInfo> out) throws Exception {
        FTPFile[] files = ftp.listFiles(currentDir);
        if (files == null) return;

        for (FTPFile f : files) {
            String name = f.getName();
            if (".".equals(name) || "..".equals(name)) continue;
            String path = joinRemote(currentDir, name);

            if (f.isDirectory()) {
                if (includeDirs && (pattern == null || pattern.matcher(name).matches())) {
                    out.add(RemoteFileInfo.builder()
                            .path(path)
                            .directory(true)
                            .sizeBytes(null)
                            .modified(f.getTimestamp() != null ? Instant.ofEpochMilli(f.getTimestamp().getTimeInMillis()) : null)
                            .build());
                }
                if (recursive) {
                    walkFtp(ftp, path, true, pattern, includeDirs, out);
                }
            } else if (f.isFile()) {
                if (pattern == null || pattern.matcher(name).matches()) {
                    out.add(RemoteFileInfo.builder()
                            .path(path)
                            .directory(false)
                            .sizeBytes(f.getSize())
                            .modified(f.getTimestamp() != null ? Instant.ofEpochMilli(f.getTimestamp().getTimeInMillis()) : null)
                            .build());
                }
            }
        }
    }

    private void ensureFtpDirectories(FTPClient ftp, String dir) throws Exception {
        String[] parts = dir.replace("\\", "/").split("/");
        StringBuilder path = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            path.append('/').append(p);
            String current = path.toString();
            if (!ftp.changeWorkingDirectory(current)) {
                boolean made = ftp.makeDirectory(current);
                if (!made) throw new IllegalStateException("Failed to create FTP directory: " + current);
            }
        }
    }

    /* ============================ SFTP IMPL ============================ */

    private UploadResult doSftpUpload(Path localFile, TransferTarget t, String remoteName, long start) throws Exception {
        int port = (t.getPort() > 0) ? t.getPort() : 22;

        try (SSHClient ssh = new SSHClient()) {
            configureHostKeyVerification(ssh, t);

            ssh.setConnectTimeout(t.getConnectionTimeoutMs());
            ssh.connect(t.getHost(), port);
            ssh.getTransport().setTimeoutMs(t.getSocketTimeoutMs());
            authenticate(ssh, t);

            try (SFTPClient sftp = ssh.newSFTPClient()) {
                if (t.isCreateDirectories() && notBlank(t.getRemoteDirectory())) {
                    sftp.mkdirs(normalizedDir(t.getRemoteDirectory()));
                }

                String remotePath = composeRemotePath(t.getRemoteDirectory(), remoteName);

                if (!t.isOverwrite()) {
                    try { sftp.stat(remotePath); throw new IllegalStateException("Remote exists and overwrite=false: " + remotePath); }
                    catch (net.schmizz.sshj.sftp.SFTPException e) { /* not found => OK */ }
                }

                long bytes = Files.size(localFile);
                sftp.put(new FileSystemFile(localFile.toFile()), remotePath);

                log.info("Processed file {}, Type: {}, Sender: {}, Receiver: {}",
                        localFile.getFileName(), "SFTP", t.getUsername(), t.getHost());

                return UploadResult.builder()
                        .success(true)
                        .protocol("SFTP")
                        .host(t.getHost())
                        .remotePath(remotePath)
                        .bytes(bytes)
                        .durationMs(System.currentTimeMillis() - start)
                        .message("OK @ " + Instant.now())
                        .build();
            }
        }
    }

    private List<RemoteFileInfo> listSftp(TransferTarget t, String dir, ListOptions opts) throws Exception {
        int port = (t.getPort() > 0) ? t.getPort() : 22;

        try (SSHClient ssh = new SSHClient()) {
            configureHostKeyVerification(ssh, t);

            ssh.setConnectTimeout(t.getConnectionTimeoutMs());
            ssh.connect(t.getHost(), port);
            ssh.getTransport().setTimeoutMs(t.getSocketTimeoutMs());
            authenticate(ssh, t);

            try (SFTPClient sftp = ssh.newSFTPClient()) {
                List<RemoteFileInfo> out = new ArrayList<>();
                Pattern pattern = globToPattern(opts.getGlob());
                walkSftp(sftp, dir, opts.isRecursive(), pattern, opts.isIncludeDirectories(), out);
                return out;
            }
        }
    }

    private void walkSftp(SFTPClient sftp, String currentDir, boolean recursive, Pattern pattern,
                          boolean includeDirs, List<RemoteFileInfo> out) throws Exception {
        for (RemoteResourceInfo r : sftp.ls(currentDir)) {
            String name = r.getName();
            if (".".equals(name) || "..".equals(name)) continue;
            String path = r.getPath();

            boolean isDir = r.isDirectory();
            boolean isFile = r.isRegularFile();

            if (isDir) {
                if (includeDirs && (pattern == null || pattern.matcher(name).matches())) {
                    Long size = r.getAttributes().getSize();
                    Long mtime = r.getAttributes().getMtime(); // seconds since epoch
                    out.add(RemoteFileInfo.builder()
                            .path(path)
                            .directory(true)
                            .sizeBytes(size)
                            .modified(mtime != null ? Instant.ofEpochSecond(mtime) : null)
                            .build());
                }
                if (recursive) {
                    walkSftp(sftp, path, true, pattern, includeDirs, out);
                }
            } else if (isFile) {
                if (pattern == null || pattern.matcher(name).matches()) {
                    Long size = r.getAttributes().getSize();
                    Long mtime = r.getAttributes().getMtime();
                    out.add(RemoteFileInfo.builder()
                            .path(path)
                            .directory(false)
                            .sizeBytes(size)
                            .modified(mtime != null ? Instant.ofEpochSecond(mtime) : null)
                            .build());
                }
            }
        }
    }

    /* ============================ SHARED HELPERS ============================ */

    private void authenticate(SSHClient ssh, TransferTarget t) throws Exception {
        if (t.getPrivateKey() != null && t.getPrivateKey().length > 0) {
            KeyProvider kp = (t.getPrivateKeyPassphrase() == null || t.getPrivateKeyPassphrase().isBlank())
                    ? ssh.loadKeys(Arrays.toString(t.getPrivateKey()))
                    : ssh.loadKeys(Arrays.toString(t.getPrivateKey()), t.getPrivateKeyPassphrase());
            ssh.authPublickey(t.getUsername(), kp);
        } else {
            ssh.authPassword(t.getUsername(), t.getPassword());
        }
    }

    private void configureHostKeyVerification(SSHClient ssh, TransferTarget t) {
        if (notBlank(t.getSftpHostKeyFingerprint())) {
            ssh.addHostKeyVerifier(t.getSftpHostKeyFingerprint().trim());
        } else if (t.isSftpTrustUnknownHostKeys()) {
            log.warn("SFTP: trusting ANY host key for {}:{} (dev only)", t.getHost(), (t.getPort() > 0 ? t.getPort() : 22));
            ssh.addHostKeyVerifier(new PromiscuousVerifier());
        } else {
            throw new IllegalStateException("SFTP requires host key verification: set sftpHostKeyFingerprint or enable sftpTrustAnyHostKey (dev only).");
        }
    }

    private static String composeRemotePath(String dir, String name) {
        if (!notBlank(dir)) return name;
        String d = normalizedDir(dir);
        return d + name;
    }

    private static String normalizedDir(String p) {
        if (!notBlank(p)) return "/";
        String d = p.replace("\\", "/");
        if (!d.startsWith("/")) d = "/" + d;
        if (!d.endsWith("/")) d += "/";
        return d;
    }

    private static String joinRemote(String dir, String name) {
        if (!notBlank(dir)) return name;
        String d = dir.endsWith("/") ? dir.substring(0, dir.length()-1) : dir;
        return d + "/" + name;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return null;
    }

    private static Pattern globToPattern(String glob) {
        if (glob == null || glob.isBlank()) return null;
        StringBuilder sb = new StringBuilder("^");
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*': sb.append(".*"); break;
                case '?': sb.append('.'); break;
                case '.': sb.append("\\."); break;
                default:
                    if ("+()^${}|[]\\".indexOf(c) >= 0) sb.append('\\');
                    sb.append(c);
            }
        }
        sb.append('$');
        return Pattern.compile(sb.toString());
    }

}
