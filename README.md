# EduAccess

Java desktop portal for Philippine schools: physical resource sharing (calculators, laptops, textbooks, lab kits), loans with overdue fees, and an offline digital library. Built with JavaFX, raw JDBC and a local SQLite database.

## Layout

```
pom.xml                              Maven build (Java 21, JavaFX 21, sqlite-jdbc, Apache POI, JUnit 6)
src/main/resources/
  db/schema.sql                      Tables, constraints and indexes (idempotent, runs on every start)
  db/seed.sql                        Demo accounts, items, loans, PDFs and downloads (development only)
  fxml/login.fxml                    Screen 1, the sign-in screen
  css/eduaccess.css                  Theme: navy navigation, blue physical, teal digital, amber admin
src/main/java/com/eduaccess/
  EduAccessApp.java                  JavaFX entry point: prepares the database, opens the Login screen
  ui/                                Screens, the navigation shell, the login controller and admin dialogs
  service/                           Business rules: LoanService, InventoryService, AuthService, DigitalLibraryService,
                                     UsbService, FileExportService, ReportService, LoanRules, LoanPolicy, Access
  dao/                               Raw JDBC with parameterised queries: UserDAO, PhysicalItemDAO, LoanDAO,
                                     DigitalResourceDAO, ReportDAO
  db/                                DatabaseManager (connections, transactions), DatabaseInitializer
  model/                             Records and enums that mirror the schema's CHECK values
  security/PasswordHasher.java       PBKDF2-HMAC-SHA256, JDK only
src/test/java/...                    56 JUnit tests (rules, hashing, database, services, admin actions, exports, USB)
storage/digital/                     PDFs for the digital library (demo placeholders; admins add more)
storage/photos/items/                Item photos are copied here (created on first use)
```

## Run

Run the app (JavaFX, from the project root so the relative storage paths resolve):

```bash
mvn javafx:run
```

The first start creates `data/eduaccess.db` and loads the demo data. To start with an empty database for a real deployment, run with `-Deduaccess.demoSeed=false`.

Run the tests:

```bash
mvn test
```

## Users and sign-in

There are two kinds of user. The sign-in screen offers both.

| Role | Sign-in choice | Can do |
|---|---|---|
| Student | Student | Browse the Physical Hub, borrow items, return and extend own loans, donate or lend items, use the Digital Library |
| Library admin | Library Admin | Everything students can see, plus: add physical items, add digital resources, record borrows at the counter, approve or reject donations, check items in, return borrowed items, waive fees, view the impact dashboard and export reports |

Demo accounts (passwords are in the header of `seed.sql`):

| School ID | Role | Notes |
|---|---|---|
| `2026-SHS-0001` | Student | Demo student |
| `LIB-0001` | Library Admin | Demo admin |
| `test-student` | Student | TEST ONLY, password `123` |
| `123` | Library Admin | TEST ONLY, password `admin` |

The two TEST ONLY accounts are exceptions to the 8-character password rule. Remove them before any real deployment.

## Admin tools

- **Physical Hub → + Add item**, **Inventory & Approval → All items → + Add item**, or the dashboard button: adds library stock. It is available straight away.
- **Digital Library → + Add resource**, or the dashboard button: copies a PDF into `storage/digital/` and adds it to the index. The source and license are required, and College files go in the College repository only.
- **Inventory & Approval → Borrowed items → + Record a borrow**, or the dashboard button: records a loan for a student by school ID. Only students can be looked up.

## Decisions

| Decision | Why |
|---|---|
| Two roles only: Student and Library Admin | The app serves students and the library staff who run it. |
| Faculty and Donor accounts from earlier builds become students | The Donor option is now an intention that students choose at registration. |
| `users.intention` (Borrower, Donor, Both) | Captured by the registration wizard. |
| The Physical Hub sorts available items first, or not-available items first | Students asked for an availability sort. |
| `physical_item_levels` replaces a single level column | The donation form lets a donor tick several levels. |
| `Rejected` item status | A rejected donation keeps a record. |
| Nullable `physical_items.donor_id` | `NULL` means library-owned stock, which admins add. |
| `digital_downloads` table | Feeds "modules downloaded" and the impact report. |
| `digital_resources.source` and `license` | Attribution and licensing of cached content. |
| One open loan per item, enforced by a partial unique index | The database refuses a second loan, not only the UI. |
| `LoanPolicy` value object | Fees, loan periods and extension rules are configurable in one place. |
| Extensions only while a loan is on time | Extending an overdue loan would hide a fee already owed. |
| Return moves the item to `Returned`; an admin counter check moves it to `Available` | Returned items are not lent out again until someone has checked them. |
| Waiving a fee sets it to zero; removing the waiver recalculates it | Matches "reset the balance for equity students". |
| Download records the download and keeps the file cached offline | One action gives the student an offline copy. |
| USB copies go into an `EduAccess Resources` folder and never overwrite files | Keeps the user's own files safe. |
| Exports include every record, not only the date range on screen | Simple, complete reports. |
| "Available for your level" is a plain level match | No AI involved; the AI assistant stays a placeholder. |

## Not built yet

- AI Study Assistant (its screen shows "Feature not available for now").
- "Save draft" on the donation form, campus announcements, the English/Filipino toggle.
- Overdue alerts sent to borrowers, and guardian consent for minors (RA 10173).
- A limit on loans per student (the design mentions one but gives no number).

## Open questions

1. How many loans may one student hold at once?
2. How is guardian consent for minors recorded?
3. The mockup footers say "H2 Database v2.2", but the app uses SQLite. Update the mockups or the document before submission.
4. The mockup shows lab kits in "Complete" condition, which is not one of the three conditions in the schema (Like New, Good, Fair).
