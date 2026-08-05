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
        String savedFilePath = UPLOAD_DIR + "/" + UUID.randomUUID() + "_" + originalFilename;
        byte[] bytes = file.getBytes();
        Path path = Paths.get(savedFilePath);
        Files.write(path, bytes);
        log.info("[FileStorageService] File saved successfully to {}", savedFilePath);
        return savedFilePath;
    }
}
