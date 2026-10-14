package com.eduaccess.service;

import com.eduaccess.dao.DigitalResourceDAO;
import com.eduaccess.db.DatabaseManager;
import com.eduaccess.model.DigitalResource;
import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.NewResource;
import com.eduaccess.model.ResourceTab;
import com.eduaccess.model.User;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Browsing the local PDF index, keeping resources available offline, and letting admins add new PDFs. */
public final class DigitalLibraryService {

    private static final double BYTES_PER_MB = 1024.0 * 1024.0;

    private final DatabaseManager db;
    private final DigitalResourceDAO resources;
    private final Path libraryRoot;

    /** @param libraryRoot folder that holds the PDFs, for example storage/digital */
    public DigitalLibraryService(DatabaseManager db, DigitalResourceDAO resources, Path libraryRoot) {
        this.db = db;
        this.resources = resources;
        this.libraryRoot = libraryRoot;
    }

    public List<DigitalResource> browse(ResourceTab tab, EducationLevel level, String text) {
        return Sql.read(db, c -> resources.searchResources(c, tab, level, text));
    }

    public List<DigitalResource> cached() {
        return Sql.read(db, resources::findCached);
    }

    /** Records the download and keeps the file cached for offline use. Returns the updated resource. */
    public DigitalResource download(User user, long resourceId) {
        return Sql.write(db, c -> {
            DigitalResource resource = resources.findById(c, resourceId)
                    .orElseThrow(() -> new ServiceException("This resource no longer exists."));
            resources.recordDownload(c, user.id(), resourceId);
            resources.toggleOfflineCache(c, resourceId, true);
            return new DigitalResource(resource.id(), resource.title(), resource.tab(), resource.subject(),
                    resource.level(), resource.filePath(), resource.fileSizeMb(), true,
                    resource.source(), resource.license());
        });
    }

    public void setCached(long resourceId, boolean cached) {
        Sql.write(db, c -> {
            resources.toggleOfflineCache(c, resourceId, cached);
            return null;
        });
    }

    /** Admin only. Copies the PDF into the library folder and adds it to the index. */
    public DigitalResource addResource(User admin, ResourceForm form) {
        Access.requireAdmin(admin, "add digital resources");
        validate(form);
        String fileName = form.pdf().getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new ServiceException("Choose a PDF file.");
        }
        Path folder = libraryRoot.resolve(form.tab() == ResourceTab.COLLEGE_REPOSITORY ? "college" : "k12");
        try {
            Files.createDirectories(folder);
            Path target = UsbService.uniqueTarget(folder, fileName);
            Files.copy(form.pdf(), target);
            double sizeMb = Files.size(target) / BYTES_PER_MB;
            String stored = target.toString().replace('\\', '/');
            NewResource resource = new NewResource(form.title().strip(), form.tab(), form.subject().strip(),
                    form.level(), stored, sizeMb, form.source().strip(), form.license().strip());
            long id = Sql.write(db, c -> resources.insert(c, resource));
            return Sql.read(db, c -> resources.findById(c, id))
                    .orElseThrow(() -> new ServiceException("The resource was saved but could not be read back."));
        } catch (IOException e) {
            throw new ServiceException("Could not copy the PDF: " + e.getMessage(), e);
        }
    }

    private static void validate(ResourceForm form) {
        if (form.title() == null || form.title().isBlank()) {
            throw new ServiceException("Enter the title.");
        }
        if (form.subject() == null || form.subject().isBlank()) {
            throw new ServiceException("Enter the subject.");
        }
        if (form.tab() == null || form.level() == null) {
            throw new ServiceException("Choose the repository and the education level.");
        }
        boolean college = form.tab() == ResourceTab.COLLEGE_REPOSITORY;
        if (college != (form.level() == EducationLevel.COLLEGE)) {
            throw new ServiceException(college
                    ? "The College Repository holds College resources only."
                    : "The K-12 Module Hub holds Elementary, JHS and SHS modules.");
        }
        if (form.source() == null || form.source().isBlank() || form.license() == null || form.license().isBlank()) {
            throw new ServiceException("Enter the source and the license, so the content can be attributed.");
        }
        if (form.pdf() == null) {
            throw new ServiceException("Choose the PDF file.");
        }
    }

    /** Values from the add-resource form. */
    public record ResourceForm(
            String title,
            String subject,
            ResourceTab tab,
            EducationLevel level,
            String source,
            String license,
            Path pdf) {
    }
}
