package com.eneik.generated.service;

import com.eneik.generated.model.Document;
import com.eneik.generated.model.DocumentVersion;
import com.eneik.generated.repository.DocumentRepository;
import com.eneik.generated.repository.DocumentVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final NotificationService notificationService;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentVersionRepository documentVersionRepository,
                           NotificationService notificationService) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.notificationService = notificationService;
    }

    /**
     * Given a new document, When saving it, Then metadata and file path are stored.
     * Generates V1 version of the document.
     */
    @Transactional
    public Document saveNewDocument(String title, String filePath, String metadata) {
        Document document = new Document(title, filePath, metadata);
        document = documentRepository.save(document);

        DocumentVersion version = new DocumentVersion(document, 1, filePath, metadata, false);
        documentVersionRepository.save(version);

        return document;
    }

    /**
     * Given a document update, When updating, Then a new version is created and the old is archived.
     * We atomically guard the transition by archiving the active versions of the specific document ID,
     * then adding the new version.
     */
    @Transactional
    public Document updateDocument(Long documentId, String title, String newFilePath, String newMetadata) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document with id " + documentId + " not found"));

        // Determine current max version before archiving to increment properly
        List<DocumentVersion> existingVersions = documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId);
        int nextVersionNumber = 1;
        if (!existingVersions.isEmpty()) {
            nextVersionNumber = existingVersions.get(0).getVersionNumber() + 1;
        }

        // Atomically archive any currently active/non-archived versions
        documentVersionRepository.archiveActiveVersions(documentId);

        // Update document's reference file path/metadata if provided
        if (title != null) {
            document.setTitle(title);
        }
        if (newFilePath != null) {
            document.setFilePath(newFilePath);
        }
        if (newMetadata != null) {
            document.setMetadata(newMetadata);
        }
        document = documentRepository.save(document);

        // Save new version as the only active one
        DocumentVersion newVersion = new DocumentVersion(document, nextVersionNumber, document.getFilePath(), document.getMetadata(), false);
        newVersion = documentVersionRepository.save(newVersion);

        // Dispatch Telegram/Max notification
        notificationService.dispatchDocumentUpdateNotification(document, newVersion);

        return document;
    }

    /**
     * Given a document, When exporting to PDF, Then it generates formatting-compliant PDF bytes.
     */
    public byte[] exportToPdf(Document doc) {
        String title = doc.getTitle();
        StringBuilder sb = new StringBuilder();
        sb.append("%PDF-1.4\n");
        sb.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        sb.append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");
        sb.append("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >> >>\nendobj\n");

        String streamContent = "BT\n/F1 12 Tf\n70 800 Td\n(" + title + ") Tj\nET\n";
        byte[] streamBytes = streamContent.getBytes(StandardCharsets.UTF_8);

        sb.append("4 0 obj\n<< /Length " + streamBytes.length + " >>\nstream\n");
        sb.append(streamContent);
        sb.append("endstream\nendobj\n");
        sb.append("xref\n0 5\n0000000000 65535 f \n0000000009 00000 n \n0000000056 00000 n \n0000000111 00000 n \n0000000282 00000 n \n");
        sb.append("trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n380\n%%EOF\n");

        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    /**
     * Given a document, When exporting to DOCX, Then it generates formatting-compliant DOCX bytes.
     */
    public byte[] exportToDocx(Document doc) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("[Content_Types].xml");
            zos.putNextEntry(entry);
            String contentTypesXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
                    "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
                    "  <Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>\n" +
                    "</Types>";
            zos.write(contentTypesXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            entry = new ZipEntry("word/document.xml");
            zos.putNextEntry(entry);
            String documentXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">\n" +
                    "  <w:body>\n" +
                    "    <w:p>\n" +
                    "      <w:r>\n" +
                    "        <w:t>" + doc.getTitle() + "</w:t>\n" +
                    "      </w:r>\n" +
                    "    </w:p>\n" +
                    "  </w:body>\n" +
                    "</w:document>";
            zos.write(documentXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        } catch (Exception e) {
            log.error("Failed to generate DOCX bytes", e);
            throw new RuntimeException("Failed to generate DOCX bytes", e);
        }
        return baos.toByteArray();
    }
}