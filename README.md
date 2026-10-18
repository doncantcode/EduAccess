# EduAccess

Java desktop portal for Philippine schools: a Physical Library of books, a Physical Hub of equipment (calculators, laptops, lab kits), Library Rental Requests where the library rents items from students, loans with overdue fees, and an offline digital library. Built with JavaFX, raw JDBC and a local SQLite database.

## Layout

```
pom.xml                              Maven build (Java 21, JavaFX 21, sqlite-jdbc, Apache POI, JUnit 6)
src/main/resources/
  db/schema.sql                      Tables, constraints and indexes (idempotent, runs on every start)
  db/seed.sql                        Demo accounts, items, loans, PDFs and downloads (development only)
  fxml/login.fxml                    Screen 1, the sign-in screen
  css/styles.css                     Flat theme over Modena: navy navigation, blue physical, teal digital/AI, amber alerts
src/main/java/com/eduaccess/
  EduAccessApp.java                  JavaFX entry point: prepares the database, opens the Login screen
  ui/                                Screens, the BorderPane shell (AppShell), dialogs (AdminForms, RentalDialogs), Icons, Lang
  service/                           Business rules: LoanService, InventoryService, RentalService, AuthService, DigitalLibraryService,
                                     UsbService, FileExportService, ReportService, LoanRules, LoanPolicy, Access
  dao/                               Raw JDBC with parameterised queries: UserDAO, PhysicalItemDAO, LoanDAO, RentalDAO,
                                     DigitalResourceDAO, ReportDAO
  db/                                DatabaseManager (connections, transactions), DatabaseInitializer
  model/                             Records and enums that mirror the schema's CHECK values
  security/PasswordHasher.java       PBKDF2-HMAC-SHA256, JDK only
src/test/java/...                    68 JUnit tests (rules, hashing, database, services, rentals, admin actions, exports, USB)
storage/digital/                     PDFs for the digital library (demo placeholders; admins add more)
storage/photos/items/                Item photos are copied here (created on first use)
```

## Run

Run the app (JavaFX, from the project root so the relative storage paths resolve):

```bash
mvn javafx:run
```

The first start creates `data/eduaccess.db` and loads the demo data. The demo data only loads into an empty database, so delete `data/eduaccess.db` once to see the demo books and rental requests added in this version (the new tables are created either way). To start with an empty database for a real deployment, run with `-Deduaccess.demoSeed=false`.

Run the tests:

```bash
mvn test
```

## Users and sign-in

There are two kinds of user. The sign-in screen offers both.

| Role | Sign-in choice | Can do |
|---|---|---|
| Student | Student | Browse the Physical Library and Physical Hub, borrow items, return and extend own loans, donate items, offer items for Library Rental Requests, use the Digital Library |
| Library admin | Library Admin | Everything students can see, plus: create rental requests and approve, check in and return offered items, add physical items, add digital resources, record borrows at the counter, approve or reject donations, check items in, return borrowed items, waive fees, view the impact dashboard and export reports |

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

## Screens

The window is a `BorderPane`: a navy sidebar on the left, and beside it the header (global search with Ctrl+K, EN/FIL toggle, notifications, profile), the current screen and a status bar.

| Sidebar tab | What it shows |
|---|---|
| Dashboard | Loans, deadlines, items for your level, and "The library needs your help" when a rental request is open |
| Physical Library | Textbooks, learner modules and reading material (category `Textbooks`) |
| Physical Hub | Equipment only: calculators, laptops and tablets, lab and study kits. No rooms or facilities |
| Library Rental Requests | Students: the request board with **Offer Item for Rent** and "Your offers". Staff: **Create New Rental Request**, review queue, check-in and return |
| Donate an Item | Permanent gifts only, with a live listing preview |
| My Loans, Digital Library, AI Study Assistant | As before |

Physical Library and Library Rental Requests are two tabs of one page; each also has its own sidebar entry.

## Library Rental Requests

The library posts what it needs to rent for a short time (a projector for a fair, easels for an exhibit) and how much it pays per item. Students 18 or older offer their own items. Offered items never enter `physical_items`, because they belong to the student and are not lent to anyone else.

| Request status | Meaning |
|---|---|
| Open | Taking offers until `offers_close_on` |
| Filled | Enough offers approved, or offers closed with at least one approved |
| Completed | Every checked-in item was handed back |
| Expired | Offers closed with nothing approved |
| Cancelled | Staff cancelled it |

| Offer status | Meaning |
|---|---|
| Offered | Waiting for staff review (students see "Waiting for review") |
| Approved | Staff accepted it. The student brings the item to the drop-off point |
| Checked-In | The library has the item. Its condition on arrival is recorded |
| Returned | Handed back to the owner. Condition and fee payment are recorded |
| Declined, Withdrawn, Cancelled | Staff said no (or the request filled or closed), the student took it back, or the request was cancelled or the item never arrived |

**Only students 18 or older can offer items.** EduAccess stores no birth date, so the rule has three layers: Elementary and Junior High accounts cannot offer at all (the board says so instead of showing the button), the student ticks "I am 18 years old or older" on the offer, and staff tick "I checked the school ID" before they can check the item in.

`RentalService.refreshDeadlines` brings requests in line with today's date whenever a screen loads, the same way overdue loans are refreshed. A database index allows one live offer per student per request.

## Decisions

| Decision | Why |
|---|---|
| Two roles only: Student and Library Admin | The app serves students and the library staff who run it. |
| Faculty and Donor accounts from earlier builds become students | The Donor option is now an intention that students choose at registration. |
| `users.intention` (Borrower, Donor, Both) | Captured by the registration wizard. |
| Physical Library and Physical Hub split by category, not by a column | `ItemCategory.catalog()` decides the tab, so no migration is needed. Textbooks are the library, everything else the hub. |
| Donate an Item no longer offers "Lend" | Donations are permanent. Lending to the library goes through Library Rental Requests. Existing `Lend` rows are kept. |
| `rental_requests` and `rental_offers` are separate from `physical_items` | A student's item rented by the library is never lent to other students. |
| The library always pays, and staff set the amount | `rental_fee` is required and above zero, per item for the whole rental. Staff record the payment when the item goes back. |
| Only students 18 or older may offer items for rent | A rental is an agreement with the owner, and RA 10173 asks for care with minors' data. Enforced in `RentalService` and at check-in. |
| The Physical Hub has no rooms or facilities | Out of scope. It lists equipment only. |
| `styles.css` replaces `eduaccess.css` | Flat cards, a full control restyle and keyboard focus rings. `:focus-visible` and `:focus-within` need JavaFX 19 or later (the build uses 21). |
| The EN/FIL toggle translates the sidebar and header only | Screen content is still English. The Filipino labels should be checked by a native speaker. |
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
- "Save draft" on the donation form, campus announcements, Filipino text for screen content (the toggle covers the sidebar and header).
- Overdue alerts sent to borrowers, and guardian consent for minors (RA 10173).
- A limit on loans per student (the design mentions one but gives no number).

## Open questions

1. How many loans may one student hold at once?
2. How is guardian consent for minors recorded?
3. The mockup footers say "H2 Database v2.2", but the app uses SQLite. Update the mockups or the document before submission.
4. The mockup shows lab kits in "Complete" condition, which is not one of the three conditions in the schema (Like New, Good, Fair).
