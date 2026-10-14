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

    /** Creates a student account from the three-step wizard. The password array is wiped afterwards. */
    public User register(RegistrationForm form) {
        try {
            validate(form);
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
