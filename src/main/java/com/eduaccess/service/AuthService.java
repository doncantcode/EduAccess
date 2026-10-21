package com.eduaccess.service;

import com.eduaccess.dao.UserDAO;
import com.eduaccess.db.DatabaseManager;
import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.Intention;
import com.eduaccess.model.NewUser;
import com.eduaccess.model.Role;
import com.eduaccess.model.User;
import com.eduaccess.security.PasswordHasher;
import java.util.Arrays;
import java.util.Optional;

/** Sign-in and student registration. */
public final class AuthService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final DatabaseManager db;
    private final UserDAO users;

    public AuthService(DatabaseManager db, UserDAO users) {
        this.db = db;
        this.users = users;
    }

    /** Checks the school ID, password and the selected role. The password array is wiped afterwards. */
    public User login(String schoolId, char[] password, LoginRole role) {
        try {
            String id = clean(schoolId);
            if (id.isEmpty() || password.length == 0) {
                throw new ServiceException("Enter your school ID and password.");
            }
            Optional<User> found = Sql.read(db, c -> users.authenticate(c, id, password));
            User user = found.orElseThrow(() -> new ServiceException("School ID or password is incorrect."));
            if (!role.accepts(user)) {
                throw new ServiceException("This account does not sign in as " + role.label() + ".");
            }
            return user;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /** Creates a student account with no guardian consent: for students 18 or older. */
    public User register(RegistrationForm form) {
        return register(form, null);
    }

    /**
     * Creates a student account from the three-step wizard. Learners under 18 need a guardian's consent
     * (RA 10173): Elementary and Junior High always, Senior High and College when the student says so by passing
     * a consent. The password array is wiped afterwards.
     */
    public User register(RegistrationForm form, GuardianConsent guardian) {
        try {
            validate(form);
            if (guardian == null && alwaysMinor(form.level())) {
                throw new ServiceException("Learners under 18 need a parent's or guardian's consent to register.");
            }
            if (guardian != null) {
                validate(guardian);
            }
            String schoolId = clean(form.schoolId());
            String hash = PasswordHasher.hash(form.password());
            NewUser account = new NewUser(
                    schoolId,
                    hash,
                    form.fullName().strip(),
                    Role.STUDENT,
                    form.intention(),
                    form.level(),
                    blankToNull(form.program()),
                    form.yearLevel(),
                    form.campus());
            return Sql.write(db, c -> {
                if (users.findBySchoolId(c, schoolId).isPresent()) {
                    throw new ServiceException("This school ID is already registered.");
                }
                long id = users.registerUser(c, account);
                if (guardian != null) {
                    users.insertGuardianConsent(c, id, guardian.name().strip(), guardian.relationship().strip(),
                            guardian.contactNumber().strip());
                }
                return users.getProfile(c, id).orElseThrow();
            });
        } finally {
            Arrays.fill(form.password(), '\0');
        }
    }

    /** Finds a student by school ID, for a borrow that an admin records at the counter. */
    public User findStudent(String schoolId) {
        String id = clean(schoolId);
        if (id.isEmpty()) {
            throw new ServiceException("Enter the student's school ID.");
        }
        return Sql.read(db, c -> users.findBySchoolId(c, id))
                .filter(user -> user.role() == Role.STUDENT)
                .orElseThrow(() -> new ServiceException("No student account has the school ID " + id + "."));
    }

    public void resetPassword(long userId, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new ServiceException("Use a password of at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        String hash = PasswordHasher.hash(newPassword.toCharArray());
        Sql.write(db, c -> {
            users.updatePassword(c, userId, hash);
            return null;
        });
    }

    public User resetStudentPasswordByAdmin(String adminSchoolId, char[] adminPassword, String studentSchoolId, String newPassword) {
        String adminId = clean(adminSchoolId);
        String sId = clean(studentSchoolId);
        if (adminId.isEmpty() || adminPassword.length == 0) {
            throw new ServiceException("Enter admin school ID and password.");
        }
        if (sId.isEmpty()) {
            throw new ServiceException("Enter the student school ID.");
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new ServiceException("Use a password of at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        try {
            return Sql.write(db, c -> {
                User admin = users.authenticate(c, adminId, adminPassword)
                        .orElseThrow(() -> new ServiceException("Admin credentials are incorrect."));
                if (admin.role() != Role.ADMIN) {
                    throw new ServiceException("The specified account is not a library administrator.");
                }
                User student = users.findBySchoolId(c, sId)
                        .filter(u -> u.role() == Role.STUDENT)
                        .orElseThrow(() -> new ServiceException("No student account has the school ID " + sId + "."));

                String hash = PasswordHasher.hash(newPassword.toCharArray());
                users.updatePassword(c, student.id(), hash);
                return student;
            });
        } finally {
            Arrays.fill(adminPassword, '\0');
        }
    }

    public void requestPasswordReset(String schoolId) {
        String id = clean(schoolId);
        if (id.isEmpty()) {
            throw new ServiceException("Enter your school ID.");
        }
        Sql.write(db, c -> {
            User user = users.findBySchoolId(c, id)
                    .orElseThrow(() -> new ServiceException("No account has the school ID " + id + "."));
            users.insertPasswordResetRequest(c, user.id());
            return null;
        });
    }

    public java.util.List<com.eduaccess.model.PasswordResetRequest> pendingPasswordResetRequests(User admin) {
        if (admin.role() != Role.ADMIN) {
            throw new ServiceException("Only administrators can view password reset requests.");
        }
        return Sql.read(db, c -> users.pendingPasswordResetRequests(c));
    }

    public void approvePasswordResetRequest(User admin, long requestId, String newPassword) {
        if (admin.role() != Role.ADMIN) {
            throw new ServiceException("Only administrators can approve password reset requests.");
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new ServiceException("Use a password of at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        String hash = PasswordHasher.hash(newPassword.toCharArray());
        Sql.write(db, c -> {
            var req = users.findPasswordResetRequest(c, requestId)
                    .orElseThrow(() -> new ServiceException("Password reset request not found."));
            users.updatePassword(c, req.userId(), hash);
            users.resolvePasswordResetRequest(c, requestId, admin.id());
            return null;
        });
    }

    public void rejectPasswordResetRequest(User admin, long requestId) {
        if (admin.role() != Role.ADMIN) {
            throw new ServiceException("Only administrators can reject password reset requests.");
        }
        Sql.write(db, c -> {
            users.rejectPasswordResetRequest(c, requestId, admin.id());
            return null;
        });
    }

    private static void validate(RegistrationForm form) {
        if (clean(form.schoolId()).isEmpty()) {
            throw new ServiceException("Enter your school ID.");
        }
        if (form.fullName() == null || form.fullName().isBlank()) {
            throw new ServiceException("Enter your full name.");
        }
        if (form.password().length < MIN_PASSWORD_LENGTH) {
            throw new ServiceException("Use a password of at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (form.level() == null) {
            throw new ServiceException("Choose your education level.");
        }
        if (form.intention() == null) {
            throw new ServiceException("Choose whether you will borrow, donate or both.");
        }
        if (form.campus() == null || form.campus().isBlank()) {
            throw new ServiceException("Choose your campus.");
        }
        int[] range = yearRange(form.level());
        if (form.yearLevel() < range[0] || form.yearLevel() > range[1]) {
            throw new ServiceException("Year level must be between " + range[0] + " and " + range[1]
                    + " for " + form.level().dbValue() + ".");
        }
        if (form.level() == EducationLevel.SHS || form.level() == EducationLevel.COLLEGE) {
            if (form.program() == null || form.program().isBlank()) {
                throw new ServiceException("Choose your " + (form.level() == EducationLevel.SHS
                        ? "strand." : "program."));
            }
        }
    }

    /** Elementary and Junior High students are always under 18. */
    public static boolean alwaysMinor(EducationLevel level) {
        return level == EducationLevel.ELEMENTARY || level == EducationLevel.JHS;
    }

    /** True when the student registered with a guardian's consent, meaning they are under 18. */
    public boolean isMinor(User user) {
        return Sql.read(db, c -> users.isMinor(c, user.id()));
    }

    private static void validate(GuardianConsent g) {
        if (g.name() == null || g.name().isBlank()) {
            throw new ServiceException("Enter your parent's or guardian's full name.");
        }
        if (g.relationship() == null || g.relationship().isBlank()) {
            throw new ServiceException("Choose how the guardian is related to you.");
        }
        String digits = g.contactNumber() == null ? "" : g.contactNumber().replaceAll("[\\s()+-]", "");
        if (!digits.matches("\\d{7,13}")) {
            throw new ServiceException("Enter the guardian's mobile or phone number, for example 0917 123 4567.");
        }
        if (!g.consented()) {
            throw new ServiceException("Your parent or guardian must agree before you can register.");
        }
    }

    /** Grade range for K-12 levels, year range for College. */
    static int[] yearRange(EducationLevel level) {
        return switch (level) {
            case ELEMENTARY -> new int[] {1, 6};
            case JHS -> new int[] {7, 10};
            case SHS -> new int[] {11, 12};
            case COLLEGE -> new int[] {1, 5};
        };
    }

    private static String clean(String schoolId) {
        return schoolId == null ? "" : schoolId.strip();
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    /** A parent's or guardian's consent for a learner under 18, collected by the registration wizard. */
    public record GuardianConsent(String name, String relationship, String contactNumber, boolean consented) {
    }

    /** The wizard's collected values. The password is cleared once the account is saved. */
    public record RegistrationForm(
            String schoolId,
            String fullName,
            char[] password,
            EducationLevel level,
            String program,
            int yearLevel,
            String campus,
            Intention intention) {
    }
}
