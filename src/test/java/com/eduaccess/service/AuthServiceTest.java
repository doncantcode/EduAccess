package com.eduaccess.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eduaccess.dao.UserDAO;
import com.eduaccess.db.DatabaseInitializer;
import com.eduaccess.db.DatabaseManager;
import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.Intention;
import com.eduaccess.model.Role;
import com.eduaccess.model.User;
import com.eduaccess.service.AuthService.GuardianConsent;
import com.eduaccess.service.AuthService.RegistrationForm;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthServiceTest {

    @TempDir
    Path tempDir;

    private AuthService auth;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseInitializer.initialize(tempDir.resolve("eduaccess.db"), false);
        auth = new AuthService(new DatabaseManager(tempDir.resolve("eduaccess.db")), new UserDAO());
    }

    @Test
    void registeredStudentCanSignInWithTheirPassword() {
        auth.register(form("2026-COL-0100", "Password1!", EducationLevel.COLLEGE, "BS Education", 2, Intention.BOTH));

        User user = auth.login("2026-COL-0100", "Password1!".toCharArray(), LoginRole.STUDENT);

        assertEquals("2026-COL-0100", user.schoolId());
        assertEquals(2, user.yearLevel());
    }

    @Test
    void wrongPasswordIsRejectedWithoutSayingWhichPartWasWrong() {
        auth.register(form("2026-COL-0100", "Password1!", EducationLevel.COLLEGE, "BS Education", 2, Intention.BOTH));

        ServiceException error = assertThrows(ServiceException.class,
                () -> auth.login("2026-COL-0100", "wrong-password".toCharArray(), LoginRole.STUDENT));
        assertEquals("School ID or password is incorrect.", error.getMessage());
    }

    @Test
    void duplicateSchoolIdIsRefused() {
        auth.register(form("2026-COL-0100", "Password1!", EducationLevel.COLLEGE, "BS Education", 2, Intention.BOTH));

        assertThrows(ServiceException.class, () ->
                auth.register(form("2026-COL-0100", "Password2!", EducationLevel.COLLEGE, "BS Education", 2,
                        Intention.BOTH)));
    }

    @Test
    void studentCannotSignInAsLibraryAdmin() {
        auth.register(form("2026-JHS-0100", "Password1!", EducationLevel.JHS, null, 8, Intention.BORROWER), GUARDIAN);

        assertThrows(ServiceException.class,
                () -> auth.login("2026-JHS-0100", "Password1!".toCharArray(), LoginRole.LIBRARY_ADMIN));
    }

    @Test
    void studentWhoChoseToDonateSignsInAsAStudent() {
        auth.register(form("2026-SHS-0100", "Password1!", EducationLevel.SHS, "HUMSS", 12, Intention.DONOR));

        User user = auth.login("2026-SHS-0100", "Password1!".toCharArray(), LoginRole.STUDENT);

        assertEquals(Role.STUDENT, user.role());
    }

    @Test
    void shortPasswordIsRefused() {
        assertThrows(ServiceException.class, () ->
                auth.register(form("2026-JHS-0101", "short", EducationLevel.JHS, null, 8, Intention.BORROWER)));
    }

    @Test
    void yearLevelMustFitTheChosenLevel() {
        assertThrows(ServiceException.class, () ->
                auth.register(form("2026-JHS-0102", "Password1!", EducationLevel.JHS, null, 11, Intention.BORROWER)));
    }

    @Test
    void seniorHighStudentMustChooseAStrand() {
        assertThrows(ServiceException.class, () ->
                auth.register(form("2026-SHS-0102", "Password1!", EducationLevel.SHS, "  ", 11, Intention.BORROWER)));
    }

    @Test
    void juniorHighStudentsNeedAGuardiansConsent() {
        assertThrows(ServiceException.class, () ->
                auth.register(form("2026-JHS-0200", "Password1!", EducationLevel.JHS, null, 8, Intention.BORROWER)));
        assertThrows(ServiceException.class, () ->
                auth.register(form("2026-JHS-0201", "Password1!", EducationLevel.JHS, null, 8, Intention.BORROWER),
                        new GuardianConsent("Maria Santos", "Mother", "0917 123 4567", false)));
        assertThrows(ServiceException.class, () ->
                auth.register(form("2026-JHS-0202", "Password1!", EducationLevel.JHS, null, 8, Intention.BORROWER),
                        new GuardianConsent("Maria Santos", "Mother", "call me", true)));

        User learner = auth.register(form("2026-JHS-0203", "Password1!", EducationLevel.JHS, null, 8,
                Intention.BORROWER), GUARDIAN);
        assertTrue(auth.isMinor(learner));
    }

    @Test
    void seniorHighStudentsUnder18CanRegisterWithConsentAndAdultsWithout() {
        User minor = auth.register(form("2026-SHS-0300", "Password1!", EducationLevel.SHS, "STEM", 11,
                Intention.BOTH), GUARDIAN);
        User adult = auth.register(form("2026-SHS-0301", "Password1!", EducationLevel.SHS, "STEM", 12,
                Intention.BOTH));

        assertTrue(auth.isMinor(minor));
        assertFalse(auth.isMinor(adult));
    }

    private static final GuardianConsent GUARDIAN =
            new GuardianConsent("Maria Santos", "Mother", "0917 123 4567", true);

    private static RegistrationForm form(String schoolId, String password, EducationLevel level, String program,
                                         int year, Intention intention) {
        return new RegistrationForm(schoolId, "Test Person", password.toCharArray(), level, program, year,
                "Main Campus", intention);
    }
}
