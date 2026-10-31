package com.eduaccess.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eduaccess.dao.PhysicalItemDAO;
import com.eduaccess.dao.UserDAO;
import com.eduaccess.db.DatabaseInitializer;
import com.eduaccess.db.DatabaseManager;
import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.Intention;
import com.eduaccess.model.PhysicalItem;
import com.eduaccess.model.User;
import com.eduaccess.service.AiRecommendationService.RecommendationResult;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiRecommendationServiceTest {

    @TempDir
    Path tempDir;

    private DatabaseManager db;
    private PhysicalItemDAO items;
    private AiRecommendationService aiService;
    private User student;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = tempDir.resolve("eduaccess.db");
        DatabaseInitializer.initialize(dbFile, true);
        db = new DatabaseManager(dbFile);
        items = new PhysicalItemDAO();
        aiService = new AiRecommendationService(db, items);

        student = new User(7, "2026-SHS-0001", "Jacob Cruz", com.eduaccess.model.Role.STUDENT,
                Intention.BORROWER, EducationLevel.SHS, "STEM", 11, "Main Campus");
    }

    @Test
    void blankQueryThrowsServiceException() {
        assertThrows(ServiceException.class, () -> aiService.recommendBooks(student, ""));
        assertThrows(ServiceException.class, () -> aiService.recommendBooks(student, "   "));
        assertThrows(ServiceException.class, () -> aiService.recommendBooks(student, null));
    }

    @Test
    void localMatcherRecommendsCalculusBookForDerivativesQuery() {
        RecommendationResult result = aiService.localMatch(student, "I need help understanding derivatives and integrals in calculus", getAvailableBooks());

        assertNotNull(result);
        assertFalse(result.recommendedBooks().isEmpty());
        assertTrue(result.recommendedBooks().stream().anyMatch(b -> b.title().toLowerCase().contains("calculus")));
        assertTrue(result.advice().contains("calculus"));
    }

    @Test
    void localMatcherRecommendsProgrammingBooksForCodingQuery() {
        RecommendationResult result = aiService.localMatch(student, "I want to learn how to code and write algorithms in Java", getAvailableBooks());

        assertNotNull(result);
        assertFalse(result.recommendedBooks().isEmpty());
        assertTrue(result.recommendedBooks().stream().anyMatch(b ->
                b.title().toLowerCase().contains("java") || b.title().toLowerCase().contains("programming") || b.title().toLowerCase().contains("data structures")));
    }

    @Test
    void localMatcherRecommendsFilipinoLiteratureBooks() {
        RecommendationResult result = aiService.localMatch(student, "We are studying Jose Rizal novels for Philippine literature", getAvailableBooks());

        assertNotNull(result);
        assertFalse(result.recommendedBooks().isEmpty());
        assertTrue(result.recommendedBooks().stream().anyMatch(b ->
                b.title().contains("Rizal") || b.title().contains("Noli") || b.title().contains("Fili") || b.title().contains("Wika") || b.title().contains("Panitikan")));
    }

    @Test
    void localMatcherRecommendsChemistryAndPhysicsBooks() {
        RecommendationResult result = aiService.localMatch(student, "I need reviewers for general chemistry and physics problem sets", getAvailableBooks());

        assertNotNull(result);
        assertFalse(result.recommendedBooks().isEmpty());
        assertTrue(result.recommendedBooks().stream().anyMatch(b ->
                b.title().contains("Chemistry") || b.title().contains("Physics")));
    }

    @Test
    void allSevenSuggestedPromptChipsMatchRelevantLibraryBooks() {
        List<PhysicalItem> books = getAvailableBooks();

        // 1. Derivatives & Calculus
        RecommendationResult calculus = aiService.localMatch(student, "Derivatives & Calculus problem solving", books);
        assertTrue(calculus.recommendedBooks().stream().anyMatch(b -> b.title().toLowerCase().contains("calculus")));

        // 2. Java programming & OOP
        RecommendationResult java = aiService.localMatch(student, "Java programming & Object-Oriented Design", books);
        assertTrue(java.recommendedBooks().stream().anyMatch(b -> b.title().toLowerCase().contains("java")));

        // 3. El Filibusterismo & Philippine literature
        RecommendationResult fili = aiService.localMatch(student, "El Filibusterismo & Philippine literature", books);
        assertTrue(fili.recommendedBooks().stream().anyMatch(b -> b.title().contains("Filibusterismo") || b.title().contains("Noli")));

        // 4. Senior High General Chemistry reviewer
        RecommendationResult chem = aiService.localMatch(student, "Senior High General Chemistry reviewer", books);
        assertTrue(chem.recommendedBooks().stream().anyMatch(b -> b.title().contains("Chemistry")));

        // 5. Basic Accounting & Managerial Finance
        RecommendationResult finance = aiService.localMatch(student, "Basic Accounting & Managerial Finance", books);
        assertTrue(finance.recommendedBooks().stream().anyMatch(b -> b.title().contains("Accounting") || b.title().contains("ABM") || b.title().contains("Finance")));

        // 6. Grade 8 Mathematics patterns and algebra
        RecommendationResult math8 = aiService.localMatch(student, "Grade 8 Mathematics patterns and algebra", books);
        assertTrue(math8.recommendedBooks().stream().anyMatch(b -> b.title().contains("Math") || b.title().contains("Algebra")));

        // 7. Araling Panlipunan: Philippine history & economy
        RecommendationResult ap = aiService.localMatch(student, "Araling Panlipunan: Philippine history & economy", books);
        assertTrue(ap.recommendedBooks().stream().anyMatch(b -> b.title().contains("Araling Panlipunan") || b.title().contains("Kasaysayan") || b.title().contains("Ekonomiks")));
    }

    @Test
    void apiKeyAndModelCanBeConfigured() {
        aiService.setApiKey("custom-test-key");
        assertEquals("custom-test-key", aiService.getApiKey());

        aiService.setModelName("gemini-3.8-flash");
        assertEquals("gemini-3.8-flash", aiService.getModelName());
    }

    @Test
    void recommendBooksReturnsAvailableBooks() {
        RecommendationResult result = aiService.recommendBooks(student, "Calculus practice");
        assertNotNull(result);
        assertFalse(result.recommendedBooks().isEmpty());
        // Verify every returned book is marked Available
        for (PhysicalItem book : result.recommendedBooks()) {
            assertEquals(com.eduaccess.model.ItemStatus.AVAILABLE, book.status());
        }
    }

    private List<PhysicalItem> getAvailableBooks() {
        return Sql.read(db, c -> items.filterItems(c,
                new com.eduaccess.dao.ItemFilter(List.of(com.eduaccess.model.ItemCategory.TEXTBOOKS), null, com.eduaccess.model.ItemStatus.AVAILABLE, null)));
    }
}
