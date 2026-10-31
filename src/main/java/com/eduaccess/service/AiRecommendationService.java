package com.eduaccess.service;

import com.eduaccess.dao.ItemFilter;
import com.eduaccess.dao.PhysicalItemDAO;
import com.eduaccess.db.DatabaseManager;
import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.ItemCategory;
import com.eduaccess.model.ItemStatus;
import com.eduaccess.model.PhysicalItem;
import com.eduaccess.model.User;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI Study Assistant: recommends available physical library books based on a student's problem or learning requirement.
 * Uses Google Gemini API (gemini-3.5-flash-lite) when online and an API key is present, and seamlessly falls back
 * to a built-in semantic keyword & education-level matching engine when offline or if the API is unavailable.
 */
public final class AiRecommendationService {

    public static final String DEFAULT_GEMINI_MODEL = "gemini-3.5-flash-lite";
    private static final String DEFAULT_FALLBACK_API_KEY = "";

    private final DatabaseManager db;
    private final PhysicalItemDAO items;
    private final HttpClient httpClient;
    private String apiKey;
    private String modelName = DEFAULT_GEMINI_MODEL;

    public record RecommendationResult(
            String query,
            String advice,
            List<PhysicalItem> recommendedBooks,
            boolean usedCloudAi,
            String engineName
    ) {}

    public AiRecommendationService(DatabaseManager db, PhysicalItemDAO items) {
        this(db, items, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    public AiRecommendationService(DatabaseManager db, PhysicalItemDAO items, HttpClient httpClient) {
        this.db = db;
        this.items = items;
        this.httpClient = httpClient;
        this.apiKey = resolveInitialApiKey();
    }

    private static String resolveInitialApiKey() {
        String prop = System.getProperty("gemini.api.key");
        if (prop != null && !prop.isBlank()) {
            return prop.strip();
        }
        String env = System.getenv("GEMINI_API_KEY");
        if (env != null && !env.isBlank()) {
            return env.strip();
        }
        return DEFAULT_FALLBACK_API_KEY;
    }

    public synchronized String getApiKey() {
        return apiKey;
    }

    public synchronized void setApiKey(String key) {
        this.apiKey = key == null ? "" : key.strip();
    }

    public synchronized String getModelName() {
        return modelName;
    }

    public synchronized void setModelName(String modelName) {
        if (modelName != null && !modelName.isBlank()) {
            this.modelName = modelName.strip();
        }
    }

    /**
     * Recommends books for the given student problem or question.
     *
     * @param student the user making the query (used for education level and strand/program context)
     * @param query the student's learning problem, topic, or question
     * @return RecommendationResult containing guidance and the matched available library books
     */
    public RecommendationResult recommendBooks(User student, String query) {
        if (query == null || query.isBlank()) {
            throw new ServiceException("Please describe what topic or problem you need help with.");
        }
        String cleanQuery = query.strip();

        // Fetch only books currently available in the Physical Library
        List<PhysicalItem> availableBooks = Sql.read(db, c -> items.filterItems(c,
                new ItemFilter(List.of(ItemCategory.TEXTBOOKS), null, ItemStatus.AVAILABLE, null)));

        if (availableBooks.isEmpty()) {
            return new RecommendationResult(
                    cleanQuery,
                    "There are currently no books marked as Available in the library shelves. Please check back later or explore the offline digital modules in the Digital Library tab.",
                    List.of(),
                    false,
                    "EduAccess Library Catalog"
            );
        }

        // Try cloud Gemini AI if key is set
        String currentKey = getApiKey();
        if (currentKey != null && !currentKey.isBlank()) {
            try {
                RecommendationResult cloudResult = callGemini(student, cleanQuery, availableBooks, currentKey);
                if (cloudResult != null && !cloudResult.recommendedBooks().isEmpty()) {
                    return cloudResult;
                }
            } catch (Exception e) {
                // Cloud AI call failed (network offline, rate limit, etc.). Fall back to local engine.
            }
        }

        // Fallback: Smart local semantic & curriculum matching engine
        return localMatch(student, cleanQuery, availableBooks);
    }

    /**
     * Calls Google Gemini API with the student's problem and available library catalog books.
     */
    private RecommendationResult callGemini(User student, String query, List<PhysicalItem> availableBooks, String key)
            throws IOException, InterruptedException {

        String prompt = buildPrompt(student, query, availableBooks);
        String requestJson = "{\"contents\":[{\"parts\":[{\"text\":" + escapeJson(prompt) + "}]}]}";

        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + key;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return null;
        }

        String responseBody = response.body();
        String generatedText = extractTextFromGeminiResponse(responseBody);
        if (generatedText == null || generatedText.isBlank()) {
            return null;
        }

        List<Long> recommendedIds = extractRecommendedIds(generatedText);
        List<PhysicalItem> matchedBooks = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();

        // Match IDs explicitly tagged by Gemini
        for (Long id : recommendedIds) {
            for (PhysicalItem book : availableBooks) {
                if (book.id() == id && seenIds.add(id)) {
                    matchedBooks.add(book);
                    break;
                }
            }
        }

        // If Gemini mentioned book titles in text without formatting IDs properly, detect them
        if (matchedBooks.isEmpty()) {
            for (PhysicalItem book : availableBooks) {
                if (generatedText.toLowerCase(Locale.ROOT).contains(book.title().toLowerCase(Locale.ROOT))
                        && seenIds.add(book.id())) {
                    matchedBooks.add(book);
                }
            }
        }

        // Clean up the trailing [RECOMMENDED_IDS: ...] tag from student-facing advice text
        String cleanAdvice = generatedText.replaceAll("\\[RECOMMENDED_IDS:[^\\]]*\\]", "").strip();

        return new RecommendationResult(
                query,
                cleanAdvice,
                matchedBooks,
                true,
                "Gemini AI (" + modelName + ")"
        );
    }

    private static String buildPrompt(User student, String query, List<PhysicalItem> books) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the AI Study Assistant for EduAccess, an educational resource portal for Philippine schools.\n");
        sb.append("A student needs book recommendations and study guidance for a specific learning problem or topic.\n\n");

        sb.append("STUDENT PROFILE:\n");
        if (student != null) {
            sb.append("- Name: ").append(student.fullName()).append("\n");
            if (student.educationLevel() != null) {
                sb.append("- Level: ").append(student.educationLevel().dbValue()).append("\n");
            }
            if (student.program() != null && !student.program().isBlank()) {
                sb.append("- Strand / Course: ").append(student.program()).append("\n");
            }
            if (student.yearLevel() != null) {
                sb.append("- Year / Grade: ").append(student.yearLevel()).append("\n");
            }
        } else {
            sb.append("- General Student\n");
        }

        sb.append("\nSTUDENT QUESTION / PROBLEM:\n\"").append(query).append("\"\n\n");

        sb.append("CURRENTLY AVAILABLE BOOKS IN THE PHYSICAL LIBRARY:\n");
        for (PhysicalItem book : books) {
            sb.append("- Item ID: ").append(book.id())
                    .append(" | Title: ").append(book.title())
                    .append(" | Edition/Info: ").append(book.brandModel() == null ? "N/A" : book.brandModel())
                    .append(" | Levels: ").append(book.levels().stream().map(EducationLevel::dbValue).collect(Collectors.joining(", ")))
                    .append(" | Condition: ").append(book.condition().dbValue())
                    .append("\n");
        }

        sb.append("\nINSTRUCTIONS:\n");
        sb.append("1. Provide friendly, clear, encouraging guidance explaining how to tackle this learning topic or problem.\n");
        sb.append("2. If any books in the list above directly address this topic or problem, recommend 1 to 4 of them and explain why each is helpful. End your response with: [RECOMMENDED_IDS: id1, id2, ...]\n");
        sb.append("3. IMPORTANT: If none of the available books in the library cover the student's problem or topic, explicitly state in your advice that the library currently does not have a book on that particular topic. Provide helpful general guidance on how to study the topic, and end your response with: [RECOMMENDED_IDS: none]\n");
        sb.append("4. ONLY use IDs that appear in the available books list above.\n");

        return sb.toString();
    }

    /**
     * Local semantic and topic matching when offline or when the Gemini API is unreachable.
     */
    RecommendationResult localMatch(User student, String query, List<PhysicalItem> availableBooks) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        EducationLevel studentLevel = student != null ? student.educationLevel() : null;

        // Tokenize query into words (ignoring common stop words)
        Set<String> queryWords = extractKeywords(lowerQuery);

        record ScoredBook(PhysicalItem book, int score) {}
        List<ScoredBook> scored = new ArrayList<>();

        for (PhysicalItem book : availableBooks) {
            int score = calculateMatchScore(book, lowerQuery, queryWords, studentLevel);
            if (score > 0) {
                scored.add(new ScoredBook(book, score));
            }
        }

        // Sort by score descending, then title
        scored.sort(Comparator.comparingInt(ScoredBook::score).reversed()
                .thenComparing(sb -> sb.book().title()));

        List<PhysicalItem> matches;
        if (!scored.isEmpty()) {
            matches = scored.stream().limit(4).map(ScoredBook::book).toList();
        } else {
            // No matching books for this topic in the physical library
            matches = List.of();
        }

        String advice = generateLocalAdvice(query, matches, student);

        return new RecommendationResult(
                query,
                advice,
                matches,
                false,
                "EduAccess Study Assistant (Offline Matcher)"
        );
    }

    private int calculateMatchScore(PhysicalItem book, String lowerQuery, Set<String> queryWords, EducationLevel studentLevel) {
        String title = book.title().toLowerCase(Locale.ROOT);
        String brand = book.brandModel() == null ? "" : book.brandModel().toLowerCase(Locale.ROOT);

        int topicScore = 0;

        // Direct full or phrase match
        if (lowerQuery.contains(title) || title.contains(lowerQuery)) {
            topicScore += 25;
        }

        // Keyword overlap
        for (String word : queryWords) {
            if (title.contains(word)) {
                topicScore += 8;
            }
            if (brand.contains(word)) {
                topicScore += 4;
            }
        }

        // Domain concepts and synonyms
        topicScore += checkDomainSynergies(lowerQuery, title, brand);

        // If there is no topic match at all, do not recommend this book
        if (topicScore == 0) {
            return 0;
        }

        int score = topicScore;
        // Grade level relevance bonus only if the book actually matches the topic
        if (studentLevel != null && book.levels().contains(studentLevel)) {
            score += 5;
        }

        return score;
    }

    private static int checkDomainSynergies(String query, String title, String brand) {
        int bonus = 0;
        String combined = title + " " + brand;

        // Mathematics & Calculus
        if (query.matches(".*\\b(math|calculus|derivative|integral|algebra|equation|geometry|numbers|fractions|polynomial)\\b.*")) {
            if (combined.contains("math") || combined.contains("calculus") || combined.contains("algebra")
                    || combined.contains("derivative") || combined.contains("geometry") || combined.contains("polynomial")) {
                bonus += 16;
            }
        }
        // Science & Chemistry / Physics
        if (query.matches(".*\\b(science|chemistry|reaction|physics|motion|force|biology|nature|living|solution)\\b.*")) {
            if (combined.contains("science") || combined.contains("chemistry") || combined.contains("physics")
                    || combined.contains("biology") || combined.contains("nature")) {
                bonus += 16;
            }
        }
        // Computer Science & Programming
        if (query.matches(".*\\b(code|coding|program|programming|java|software|database|algorithm|computer|data structures|oop|object-oriented)\\b.*")) {
            if (combined.contains("programming") || combined.contains("java") || combined.contains("database")
                    || combined.contains("data structures") || combined.contains("object-oriented") || combined.contains("design")) {
                bonus += 16;
            }
        }
        // Philippine Literature & History
        if (query.matches(".*\\b(rizal|history|philippine|pilipinas|kasaysayan|noli|fili|filipino|panitikan|panlipunan|wika|literature|florante)\\b.*")) {
            if (combined.contains("noli") || combined.contains("fili") || combined.contains("history") || combined.contains("pilipinas")
                    || combined.contains("panlipunan") || combined.contains("wika") || combined.contains("rizal")
                    || combined.contains("panitikan") || combined.contains("florante")) {
                bonus += 16;
            }
        }
        // Business, Economics, Accounting
        if (query.matches(".*\\b(business|finance|accounting|accountancy|abm|money|ekonomiks|economics|managerial|bookkeeping)\\b.*")) {
            if (combined.contains("abm") || combined.contains("accountancy") || combined.contains("accounting")
                    || combined.contains("finance") || combined.contains("ekonomiks") || combined.contains("economic")
                    || combined.contains("bookkeeping")) {
                bonus += 16;
            }
        }
        // English & Literature
        if (query.matches(".*\\b(english|grammar|reading|literature|story|vocabulary|comprehension|writing)\\b.*")) {
            if (combined.contains("english") || combined.contains("reading") || combined.contains("literature")
                    || combined.contains("kwento") || combined.contains("writing")) {
                bonus += 16;
            }
        }
        return bonus;
    }

    private static String generateLocalAdvice(String query, List<PhysicalItem> matches, User student) {
        StringBuilder sb = new StringBuilder();
        String name = student != null && student.fullName() != null ? student.fullName().split("\\s+")[0] : "Learner";
        sb.append("Kumusta, ").append(name).append("! ");
        sb.append("Here is your study guide based on your inquiry about \"").append(query).append("\":\n\n");
        sb.append("To understand this topic thoroughly, start by reviewing core fundamentals before tackling complex problem sets. ");

        if (!matches.isEmpty()) {
            sb.append("We found ").append(matches.size())
                    .append(matches.size() == 1 ? " physical book" : " physical books")
                    .append(" currently available at the library counter that directly cover this subject:\n\n");

            for (int i = 0; i < matches.size(); i++) {
                PhysicalItem b = matches.get(i);
                sb.append(i + 1).append(". **").append(b.title()).append("**");
                if (b.brandModel() != null && !b.brandModel().isBlank()) {
                    sb.append(" (").append(b.brandModel()).append(")");
                }
                sb.append(" - Available for borrowing at ").append(b.pickupPoint()).append(".\n");
            }
            sb.append("\nYou can borrow these items at the counter or reserve them directly using the View buttons below.");
        } else {
            sb.append("The library does not have a book on that particular topic (\"").append(query).append("\") in our physical collection at this time.\n\n");
            sb.append("While there are currently no physical books available for this subject on our shelves, here are suggested next steps:\n");
            sb.append("- Check our **Digital Library** tab for downloadable e-books or PDF learning modules.\n");
            sb.append("- Check the **Student Marketplace** to see if peers are lending notes or books on this subject.\n");
            sb.append("- Ask at **Library Counter A** to submit a book acquisition or donation request.");
        }

        return sb.toString();
    }

    private static Set<String> extractKeywords(String text) {
        Set<String> stopWords = Set.of(
                "a", "an", "the", "and", "or", "in", "on", "at", "to", "for", "with", "by", "from",
                "of", "is", "are", "am", "was", "were", "be", "been", "have", "has", "had", "do",
                "does", "did", "i", "my", "me", "we", "our", "you", "your", "he", "she", "it",
                "they", "their", "want", "need", "help", "book", "books", "about", "study", "studying"
        );
        Set<String> words = new LinkedHashSet<>();
        for (String word : text.split("[^a-zA-Z0-9]+")) {
            word = word.strip().toLowerCase(Locale.ROOT);
            if (word.length() > 2 && !stopWords.contains(word)) {
                words.add(word);
            }
        }
        return words;
    }

    private static List<Long> extractRecommendedIds(String text) {
        List<Long> ids = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\[RECOMMENDED_IDS:\\s*([0-9,\\s]+)\\]", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String idGroup = matcher.group(1);
            for (String part : idGroup.split(",")) {
                try {
                    String clean = part.strip();
                    if (!clean.isEmpty()) {
                        ids.add(Long.parseLong(clean));
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return ids;
    }

    private static String extractTextFromGeminiResponse(String json) {
        if (json == null) return null;
        StringBuilder sb = new StringBuilder();
        int searchFrom = 0;
        String key = "\"text\":";
        while (true) {
            int keyIndex = json.indexOf(key, searchFrom);
            if (keyIndex == -1) break;
            int quoteStart = json.indexOf('"', keyIndex + key.length());
            if (quoteStart == -1) break;
            StringBuilder piece = new StringBuilder();
            boolean escape = false;
            int i = quoteStart + 1;
            for (; i < json.length(); i++) {
                char c = json.charAt(i);
                if (escape) {
                    if (c == 'n') piece.append('\n');
                    else if (c == 'r') piece.append('\r');
                    else if (c == 't') piece.append('\t');
                    else piece.append(c);
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    break;
                } else {
                    piece.append(c);
                }
            }
            sb.append(piece).append("\n");
            searchFrom = i + 1;
        }
        return sb.toString().strip();
    }

    private static String escapeJson(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    private static String unescapeJson(String s) {
        return s.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
