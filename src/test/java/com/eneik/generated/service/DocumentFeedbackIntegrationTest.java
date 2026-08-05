package com.eneik.generated.service;

import com.eneik.generated.model.Document;
import com.eneik.generated.model.Comment;
import com.eneik.generated.model.ActualizationRequest;
import com.eneik.generated.repository.DocumentRepository;
import com.eneik.generated.repository.CommentRepository;
import com.eneik.generated.repository.ActualizationRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=true"
})
@Transactional
public class DocumentFeedbackIntegrationTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private ActualizationRequestRepository actualizationRequestRepository;

    @Test
    public void testCommentPersistenceAndQueries() {
        // 1. Create and save a document
        Document document = new Document("Test Document", "/path/to/file.pdf", "{}");
        document = documentRepository.save(document);
        assertNotNull(document.getId());

        // 2. Create and save comments
        Comment comment1 = new Comment(document, "user-123", "ivan.ivanov@epidem.ru", "Иванов Иван Иванович", "Administrator", "First feedback comment");
        Comment comment2 = new Comment(document, "user-456", "petr.petrov@epidem.ru", "Петров Петр Петрович", "Teacher", "Second feedback comment");

        comment1 = commentRepository.save(comment1);
        comment2 = commentRepository.save(comment2);

        assertNotNull(comment1.getId());
        assertNotNull(comment2.getId());

        // 3. Query comments by document ID
        List<Comment> comments = commentRepository.findByDocumentId(document.getId());
        assertEquals(2, comments.size());

        assertTrue(comments.stream().anyMatch(c -> c.getText().equals("First feedback comment")));
        assertTrue(comments.stream().anyMatch(c -> c.getText().equals("Second feedback comment")));

        Comment savedComment = comments.stream().filter(c -> c.getText().equals("First feedback comment")).findFirst().orElse(null);
        assertNotNull(savedComment);
        assertEquals("user-123", savedComment.getUserId());
        assertEquals("ivan.ivanov@epidem.ru", savedComment.getUsername());
        assertEquals("Иванов Иван Иванович", savedComment.getFullName());
        assertEquals("Administrator", savedComment.getUserRole());
    }

    @Test
    public void testActualizationRequestPersistenceAndQueries() {
        // 1. Create and save a document
        Document document = new Document("Another Document", "/path/to/another.pdf", "{}");
        document = documentRepository.save(document);
        assertNotNull(document.getId());

        // 2. Create and save an actualization request
        ActualizationRequest request = new ActualizationRequest(
                document,
                "user-789",
                "maria.smirnova@epidem.ru",
                "Смирнова Мария Ивановна",
                "Teacher",
                "New regulations updated",
                "PENDING"
        );

        request = actualizationRequestRepository.save(request);
        assertNotNull(request.getId());
        assertEquals("PENDING", request.getStatus());

        // 3. Query actualization requests by document ID
        List<ActualizationRequest> requests = actualizationRequestRepository.findByDocumentId(document.getId());
        assertEquals(1, requests.size());

        ActualizationRequest savedRequest = requests.get(0);
        assertEquals("New regulations updated", savedRequest.getReason());
        assertEquals("user-789", savedRequest.getRequesterId());
        assertEquals("maria.smirnova@epidem.ru", savedRequest.getRequesterUsername());
        assertEquals("Смирнова Мария Ивановна", savedRequest.getRequesterFullName());
        assertEquals("Teacher", savedRequest.getRequesterRole());
        assertEquals("PENDING", savedRequest.getStatus());

        // 4. Update status with atomic-style check or simple update
        savedRequest.setStatus("APPROVED");
        actualizationRequestRepository.save(savedRequest);

        ActualizationRequest updatedRequest = actualizationRequestRepository.findById(savedRequest.getId()).orElse(null);
        assertNotNull(updatedRequest);
        assertEquals("APPROVED", updatedRequest.getStatus());
    }

    @Test
    public void testOnDeleteCascadeConstraints() {
        // 1. Create and save a document
        Document document = new Document("Temporary Doc", "/path/to/temp.pdf", "{}");
        document = documentRepository.save(document);
        Long docId = document.getId();

        // 2. Save comment and request
        Comment comment = new Comment(document, "user-1", "user1@epidem.ru", "U1", "Student", "Comment");
        comment = commentRepository.save(comment);

        ActualizationRequest request = new ActualizationRequest(document, "user-1", "user1@epidem.ru", "U1", "Student", "Reason", "PENDING");
        request = actualizationRequestRepository.save(request);

        // Verify they are saved
        assertEquals(1, commentRepository.findByDocumentId(docId).size());
        assertEquals(1, actualizationRequestRepository.findByDocumentId(docId).size());

        // 3. Delete the document and ensure cascade deletes comments and requests
        documentRepository.delete(document);
        documentRepository.flush();
        commentRepository.flush();
        actualizationRequestRepository.flush();

        assertEquals(0, commentRepository.findByDocumentId(docId).size());
        assertEquals(0, actualizationRequestRepository.findByDocumentId(docId).size());
    }

    @Test
    public void testIdSequenceStartsAboveSeededDocuments() {
        Document newDoc = new Document("Sequence Check Doc", "/path/to/seq.pdf", "{}");
        newDoc = documentRepository.save(newDoc);
        assertNotNull(newDoc.getId());
        assertTrue(newDoc.getId() >= 100, "Expected new document ID to be at least 100 to avoid conflicts with seeded documents (1-6)");
    }
}
