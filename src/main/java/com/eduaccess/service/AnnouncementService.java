package com.eduaccess.service;

import com.eduaccess.dao.AnnouncementDAO;
import com.eduaccess.db.DatabaseManager;
import com.eduaccess.model.Announcement;
import com.eduaccess.model.User;
import java.time.LocalDate;
import java.util.List;

/** Campus announcements: staff post them, and the dashboard shows them until they end or are removed. */
public final class AnnouncementService {

    static final int TITLE_MAX = 80;
    static final int BODY_MAX = 500;

    private final DatabaseManager db;
    private final AnnouncementDAO announcements;

    public AnnouncementService(DatabaseManager db, AnnouncementDAO announcements) {
        this.db = db;
        this.announcements = announcements;
    }

    /** Posts an announcement visible from today. expiresOn may be null to keep it until staff remove it. */
    public long post(User staff, String title, String body, LocalDate expiresOn, LocalDate today) {
        Access.requireAdmin(staff, "post announcements");
        if (title == null || title.isBlank()) {
            throw new ServiceException("Enter a title for the announcement.");
        }
        if (body == null || body.isBlank()) {
            throw new ServiceException("Enter the announcement text.");
        }
        if (title.strip().length() > TITLE_MAX || body.strip().length() > BODY_MAX) {
            throw new ServiceException("Keep the title under " + TITLE_MAX + " characters and the text under "
                    + BODY_MAX + ".");
        }
        if (expiresOn != null && expiresOn.isBefore(today)) {
            throw new ServiceException("The end date cannot be in the past.");
        }
        String cleanTitle = title.strip();
        String cleanBody = body.strip();
        return Sql.write(db, c -> announcements.insert(c, staff.id(), cleanTitle, cleanBody, today, expiresOn));
    }

    /** What students see today, newest first. */
    public List<Announcement> visible(LocalDate today) {
        return Sql.read(db, c -> announcements.visibleOn(c, today));
    }

    public List<Announcement> all(User staff) {
        Access.requireAdmin(staff, "manage announcements");
        return Sql.read(db, announcements::all);
    }

    public void remove(User staff, long id) {
        Access.requireAdmin(staff, "remove announcements");
        Sql.write(db, c -> {
            if (!announcements.remove(c, id)) {
                throw new ServiceException("This announcement was already removed.");
            }
            return null;
        });
    }
}
