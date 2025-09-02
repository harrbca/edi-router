package io.github.harrbca.edirouter.cli;

import io.github.harrbca.edirouter.config.CliProperties;
import io.github.harrbca.edirouter.model.fileTransfer.*;
import io.github.harrbca.edirouter.service.FileTransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.cli", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InteractiveCliService {

    private final FileTransferService fileTransferService;
    private final CliProperties cliProperties;
    private TransferTarget currentConnection;
    private boolean running = true;

    @EventListener(ApplicationReadyEvent.class)
    public void startInteractiveCli() {
        if (cliProperties.isShowWelcomeMessage()) {
            System.out.println("\n=== EDI Router File Transfer CLI ===");
            System.out.println("Type 'help' for available commands, 'quit' to exit\n");
        }
        
        Scanner scanner = new Scanner(System.in);
        
        while (running) {
            System.out.print("edi-router> ");
            String input = scanner.nextLine().trim();
            
            if (!input.isEmpty()) {
                processCommand(input);
            }
        }
        
        scanner.close();
        System.out.println("CLI session ended.");
    }

    private void processCommand(String input) {
        String[] parts = input.split("\\s+");
        String command = parts[0].toLowerCase();
        
        try {
            switch (command) {
                case "help" -> showHelp();
                case "connect" -> handleConnect(parts);
                case "disconnect" -> handleDisconnect();
                case "status" -> showConnectionStatus();
                case "upload" -> handleUpload(parts);
                case "ls", "list" -> handleList(parts);
                case "pwd" -> showCurrentDirectory();
                case "quit", "exit" -> {
                    running = false;
                    System.out.println("Goodbye!");
                }
                default -> System.out.println("Unknown command: " + command + ". Type 'help' for available commands.");
            }
        } catch (Exception e) {
            System.err.println("Error executing command: " + e.getMessage());
            log.debug("Command error details", e);
        }
    }

    private void showHelp() {
        System.out.println("""
            Available commands:
            
            Connection Management:
              connect <protocol> <host> <username> <password> [port] [directory]
                - Connect to FTP/SFTP server
                - Example: connect ftp example.com user pass 21 /uploads
              disconnect          - Disconnect from current server
              status              - Show connection status
            
            File Operations:
              upload <local-path> [remote-name]
                - Upload a local file to connected server
                - Example: upload /path/to/file.txt custom-name.txt
              ls [directory] [pattern]
                - List remote directory contents
                - Example: ls /uploads *.txt
              pwd                 - Show current remote directory
            
            General:
              help                - Show this help message
              quit/exit           - Exit the CLI
            """);
    }

    private void handleConnect(String[] parts) {
        if (parts.length < 5) {
            System.out.println("Usage: connect <protocol> <host> <username> <password> [port] [directory]");
            System.out.println("Example: connect ftp example.com user pass 21 /uploads");
            return;
        }

        String protocol = parts[1].toUpperCase();
        String host = parts[2];
        String username = parts[3];
        String password = parts[4];
        int port = parts.length > 5 ? Integer.parseInt(parts[5]) : -1;
        String directory = parts.length > 6 ? parts[6] : "/";

        Protocol proto;
        try {
            proto = Protocol.valueOf(protocol);
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid protocol: " + protocol + ". Use FTP or SFTP");
            return;
        }

        currentConnection = TransferTarget.builder()
                .protocol(proto)
                .host(host)
                .port(port)
                .username(username)
                .password(password)
                .remoteDirectory(directory)
                .build();

        // Test the connection by listing the directory
        try {
            List<RemoteFileInfo> files = fileTransferService.listDirectory(currentConnection);
            System.out.println("✓ Connected to " + protocol + "://" + host + 
                             (port > 0 ? ":" + port : "") + directory);
            System.out.println("Found " + files.size() + " items in directory");
        } catch (Exception e) {
            System.err.println("✗ Connection failed: " + e.getMessage());
            currentConnection = null;
        }
    }

    private void handleDisconnect() {
        if (currentConnection == null) {
            System.out.println("No active connection.");
            return;
        }

        System.out.println("Disconnected from " + currentConnection.getHost());
        currentConnection = null;
    }

    private void showConnectionStatus() {
        if (currentConnection == null) {
            System.out.println("Status: Not connected");
        } else {
            System.out.println("Status: Connected");
            System.out.println("  Protocol: " + currentConnection.getProtocol());
            System.out.println("  Host: " + currentConnection.getHost() + 
                             (currentConnection.getPort() > 0 ? ":" + currentConnection.getPort() : ""));
            System.out.println("  Username: " + currentConnection.getUsername());
            System.out.println("  Directory: " + currentConnection.getRemoteDirectory());
        }
    }

    private void handleUpload(String[] parts) {
        if (currentConnection == null) {
            System.out.println("Not connected. Use 'connect' command first.");
            return;
        }

        if (parts.length < 2) {
            System.out.println("Usage: upload <local-path> [remote-name]");
            return;
        }

        String localPath = parts[1];
        String remoteName = parts.length > 2 ? parts[2] : null;

        Path localFile = Paths.get(localPath);
        if (!Files.exists(localFile)) {
            System.out.println("Local file not found: " + localPath);
            return;
        }

        TransferTarget uploadTarget = TransferTarget.builder()
                .protocol(currentConnection.getProtocol())
                .host(currentConnection.getHost())
                .port(currentConnection.getPort())
                .username(currentConnection.getUsername())
                .password(currentConnection.getPassword())
                .remoteDirectory(currentConnection.getRemoteDirectory())
                .remoteFilename(remoteName)
                .privateKey(currentConnection.getPrivateKey())
                .privateKeyPassphrase(currentConnection.getPrivateKeyPassphrase())
                .sftpHostKeyFingerprint(currentConnection.getSftpHostKeyFingerprint())
                .sftpTrustUnknownHostKeys(currentConnection.isSftpTrustUnknownHostKeys())
                .build();

        System.out.print("Uploading " + localFile.getFileName() + "... ");
        
        UploadResult result = fileTransferService.upload(localFile, uploadTarget);
        
        if (result.isSuccess()) {
            System.out.println("✓ Success");
            System.out.println("  Remote path: " + result.getRemotePath());
            System.out.println("  Bytes: " + result.getBytes());
            System.out.println("  Duration: " + result.getDurationMs() + "ms");
        } else {
            System.out.println("✗ Failed");
            System.out.println("  Error: " + result.getMessage());
        }
    }

    private void handleList(String[] parts) {
        if (currentConnection == null) {
            System.out.println("Not connected. Use 'connect' command first.");
            return;
        }

        String directory = parts.length > 1 ? parts[1] : null;
        String pattern = parts.length > 2 ? parts[2] : null;

        ListOptions options = ListOptions.builder()
                .directory(directory)
                .glob(pattern)
                .includeDirectories(true)
                .build();

        try {
            List<RemoteFileInfo> files = fileTransferService.listDirectory(currentConnection, options);
            
            if (files.isEmpty()) {
                System.out.println("No files found.");
                return;
            }

            System.out.println("Directory listing:");
            System.out.printf("%-10s %10s %20s %s%n", "Type", "Size", "Modified", "Name");
            System.out.println("-".repeat(60));
            
            for (RemoteFileInfo file : files) {
                String type = file.isDirectory() ? "DIR" : "FILE";
                String size = file.getSizeBytes() != null ? String.valueOf(file.getSizeBytes()) : "-";
                String modified = file.getModified() != null ? file.getModified().toString() : "-";
                String name = file.getPath();
                
                // Extract just the filename from full path
                if (name.contains("/")) {
                    name = name.substring(name.lastIndexOf('/') + 1);
                }
                
                System.out.printf("%-10s %10s %20s %s%n", type, size, 
                    modified.length() > 20 ? modified.substring(0, 17) + "..." : modified, name);
            }
            
            System.out.println("\nTotal: " + files.size() + " items");
            
        } catch (Exception e) {
            System.err.println("Failed to list directory: " + e.getMessage());
        }
    }

    private void showCurrentDirectory() {
        if (currentConnection == null) {
            System.out.println("Not connected.");
        } else {
            System.out.println("Current directory: " + currentConnection.getRemoteDirectory());
        }
    }
}