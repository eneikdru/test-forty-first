package com.eneik.generated.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileStorageService {
    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final String UPLOAD_DIR = "uploads";

    public FileStorageService() {
        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
        } catch (IOException e) {
            log.error("[FileStorageService] Failed to create upload directory: {}", UPLOAD_DIR, e);
        }
    }

    public String saveFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            originalFilename = "file";
        }

        // Strict validation: reject any path traversal attempts or directory characters
        if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
            throw new IllegalArgumentException("Invalid file name containing forbidden path characters: " + originalFilename);
        }

        Path originalPath = Paths.get(originalFilename);
        String cleanedFilename = originalPath.getFileName().toString();

        String savedFilePath = UPLOAD_DIR + "/" + UUID.randomUUID() + "_" + cleanedFilename;
        Path path = Paths.get(savedFilePath);

        // Explicit check: resolved path must start with the UPLOAD_DIR prefix
        Path absoluteUploadDirPath = Paths.get(UPLOAD_DIR).toAbsolutePath().normalize();
        Path absoluteDestPath = path.toAbsolutePath().normalize();
        if (!absoluteDestPath.startsWith(absoluteUploadDirPath)) {
            throw new SecurityException("Directory traversal attempt detected: " + originalFilename);
        }

        byte[] bytes = file.getBytes();
        Files.write(absoluteDestPath, bytes);
        log.info("[FileStorageService] File saved successfully to {}", absoluteDestPath);
        return savedFilePath;
    }
}
