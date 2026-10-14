package com.eduaccess.service;

import com.eduaccess.dao.ItemFilter;
import com.eduaccess.dao.PhysicalItemDAO;
import com.eduaccess.db.DatabaseManager;
import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.GivingType;
import com.eduaccess.model.ItemCategory;
import com.eduaccess.model.ItemCondition;
import com.eduaccess.model.ItemStatus;
import com.eduaccess.model.NewItem;
import com.eduaccess.model.PhysicalItem;
import com.eduaccess.model.User;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Donations, library stock added by admins, and the library's approval queue. */
public final class InventoryService {

    private static final Path PHOTO_FOLDER = Path.of("storage", "photos", "items");
    private static final Set<String> PHOTO_TYPES = Set.of("jpg", "jpeg", "png");

    private final DatabaseManager db;
    private final PhysicalItemDAO items;

    public InventoryService(DatabaseManager db, PhysicalItemDAO items) {
        this.db = db;
        this.items = items;
    }

    /** A student donates an item. It waits as Pending until the library checks it at the counter. */
    public long submitDonation(User donor, DonationForm form) {
        validate(form);
        String photoPath = copyPhoto(form.photo());
        NewItem item = new NewItem(
                donor.id(),
                form.title().strip(),
                blankToNull(form.brandModel()),
                form.category(),
                form.condition(),
                form.pickupPoint(),
                photoPath,
                ItemStatus.PENDING,
                form.givingType(),
                List.copyOf(form.levels()));
        return Sql.write(db, c -> items.insertItem(c, item));
    }

    /** An admin adds library stock. It is available straight away, because the admin is the library. */
    public long registerLibraryItem(User admin, DonationForm form) {
        Access.requireAdmin(admin, "add physical items");
        validate(form);
        String photoPath = copyPhoto(form.photo());
        NewItem item = new NewItem(
                null,
                form.title().strip(),
                blankToNull(form.brandModel()),
                form.category(),
                form.condition(),
                form.pickupPoint(),
                photoPath,
                ItemStatus.AVAILABLE,
                GivingType.DONATE,
                List.copyOf(form.levels()));
        return Sql.write(db, c -> items.insertItem(c, item));
    }

    public List<PhysicalItem> pendingApprovals() {
        return Sql.read(db, items::getPendingApprovals);
    }

    public List<PhysicalItem> allItems() {
        return Sql.read(db, items::getAllItems);
    }

    public List<PhysicalItem> browse(ItemFilter filter) {
        return Sql.read(db, c -> items.filterItems(c, filter));
    }

    public PhysicalItem find(long itemId) {
        return Sql.read(db, c -> items.findById(c, itemId))
                .orElseThrow(() -> new ServiceException("This item no longer exists."));
    }

    public void approve(long itemId) {
        move(itemId, ItemStatus.PENDING, ItemStatus.AVAILABLE, "Only pending donations can be approved.");
    }

    public void reject(long itemId) {
        move(itemId, ItemStatus.PENDING, ItemStatus.REJECTED, "Only pending donations can be rejected.");
    }

    /** Admin counter check after a return. The item goes back into circulation. */
    public void checkIn(long itemId) {
        move(itemId, ItemStatus.RETURNED, ItemStatus.AVAILABLE, "Only returned items need a counter check.");
    }

    public InventorySummary summary() {
        return Sql.read(db, c -> new InventorySummary(
                items.countAll(c),
                items.countByStatus(c, ItemStatus.AVAILABLE),
                items.countByStatus(c, ItemStatus.BORROWED),
                items.countByStatus(c, ItemStatus.OVERDUE)));
    }

    private void move(long itemId, ItemStatus from, ItemStatus to, String refusal) {
        Sql.write(db, c -> {
            if (!items.updateStatus(c, itemId, from, to)) {
                throw new ServiceException(refusal);
            }
            return null;
        });
    }

    private static void validate(DonationForm form) {
        if (form.title() == null || form.title().isBlank()) {
            throw new ServiceException("Enter the item name.");
        }
        if (form.levels() == null || form.levels().isEmpty()) {
            throw new ServiceException("Choose at least one recommended education level.");
        }
        if (form.category() == null || form.condition() == null || form.givingType() == null) {
            throw new ServiceException("Choose the item type, condition and type of giving.");
        }
        if (form.pickupPoint() == null || form.pickupPoint().isBlank()) {
            throw new ServiceException("Choose a drop-off point.");
        }
    }

    /** Copies a photo into the app folder and returns its relative path, using forward slashes. */
    private static String copyPhoto(Path photo) {
        if (photo == null) {
            return null;
        }
        String name = photo.getFileName().toString();
        String extension = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!PHOTO_TYPES.contains(extension)) {
            throw new ServiceException("Photos must be a JPG or PNG file.");
        }
        try {
            Files.createDirectories(PHOTO_FOLDER);
            Path target = PHOTO_FOLDER.resolve(UUID.randomUUID() + "." + extension);
            Files.copy(photo, target);
            return target.toString().replace('\\', '/');
        } catch (IOException e) {
            throw new ServiceException("Could not save the photo: " + e.getMessage(), e);
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    /** Values from the donation or add-item form. photo may be null. */
    public record DonationForm(
            String title,
            String brandModel,
            ItemCategory category,
            ItemCondition condition,
            String pickupPoint,
            List<EducationLevel> levels,
            GivingType givingType,
            Path photo) {
    }

    public record InventorySummary(int total, int available, int borrowed, int overdue) {
    }
}
