-- EduAccess schema for SQLite (eduaccess.db).
-- Applied on every boot by DatabaseInitializer. Every statement is idempotent.
-- Statements are split on the semicolon character, so never put one inside a comment or a string literal.

PRAGMA user_version = 1;

-- Accounts. Login uses school_id + password_hash; role decides permissions and the login radio button.
CREATE TABLE IF NOT EXISTS users (
    user_id          INTEGER PRIMARY KEY AUTOINCREMENT,
    school_id        TEXT    NOT NULL UNIQUE,
    password_hash    TEXT    NOT NULL,
    full_name        TEXT    NOT NULL,
    role             TEXT    NOT NULL CHECK (role IN ('Student', 'Admin')),
    intention        TEXT    CHECK (intention IN ('Borrower', 'Donor', 'Both')),  -- from the registration wizard, NULL for staff
    education_level  TEXT    CHECK (education_level IN ('Elementary', 'JHS', 'SHS', 'College')),
    program          TEXT,                                                       -- college course, or SHS strand (STEM, ABM, HUMSS, TVL)
    year_level       INTEGER CHECK (year_level BETWEEN 1 AND 12),                -- grade number (K-12) or year number (College)
    campus           TEXT,
    created_at       TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- Physical catalog: donated and lent calculators, laptops and tablets, textbooks and lab kits.
-- donor_id is NULL for library-owned stock.
CREATE TABLE IF NOT EXISTS physical_items (
    item_id         INTEGER PRIMARY KEY AUTOINCREMENT,
    donor_id        INTEGER REFERENCES users (user_id) ON DELETE SET NULL,
    title           TEXT    NOT NULL,
    brand_model     TEXT,
    category        TEXT    NOT NULL CHECK (category IN ('Calculators', 'Laptops & Tablets', 'Textbooks', 'Lab/Study Kits')),
    condition       TEXT    NOT NULL CHECK (condition IN ('Like New', 'Good', 'Fair')),
    pickup_point    TEXT    NOT NULL,
    photo_path      TEXT,                                                      -- relative path, NULL until a photo is attached
    status          TEXT    NOT NULL DEFAULT 'Pending'
                    CHECK (status IN ('Pending', 'Available', 'Borrowed', 'Overdue', 'Returned', 'Rejected')),
    type_of_giving  TEXT    NOT NULL CHECK (type_of_giving IN ('Donate', 'Lend')),
    created_at      TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- Several levels per item, because the donation form lets the donor tick more than one "Recommended for" box.
CREATE TABLE IF NOT EXISTS physical_item_levels (
    item_id         INTEGER NOT NULL REFERENCES physical_items (item_id) ON DELETE CASCADE,
    education_level TEXT    NOT NULL CHECK (education_level IN ('Elementary', 'JHS', 'SHS', 'College')),
    PRIMARY KEY (item_id, education_level)
);

-- Loans. Dates are ISO-8601 text (yyyy-MM-dd) and are parsed with java.time.LocalDate.
-- fee_amount is the accrued overdue fee as of the last update (see LoanPolicy for the daily rate).
-- fee_waived marks an equity-program waiver granted by the librarian.
CREATE TABLE IF NOT EXISTS loans (
    loan_id          INTEGER PRIMARY KEY AUTOINCREMENT,
    borrower_id      INTEGER NOT NULL REFERENCES users (user_id) ON DELETE RESTRICT,
    item_id          INTEGER NOT NULL REFERENCES physical_items (item_id) ON DELETE RESTRICT,
    borrow_date      TEXT    NOT NULL,
    due_date         TEXT    NOT NULL,
    return_date      TEXT,
    loan_period_days INTEGER NOT NULL CHECK (loan_period_days IN (3, 7, 14)),
    extension_count  INTEGER NOT NULL DEFAULT 0 CHECK (extension_count BETWEEN 0 AND 1),
    fee_amount       NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (fee_amount >= 0),
    fee_waived       INTEGER NOT NULL DEFAULT 0 CHECK (fee_waived IN (0, 1)),
    status           TEXT    NOT NULL CHECK (status IN ('Active', 'Overdue', 'Returned')),
    CHECK (due_date >= borrow_date),
    CHECK ((status = 'Returned') = (return_date IS NOT NULL))
);

-- At most one open loan (Active or Overdue) per item. The database enforces this, not only the UI.
CREATE UNIQUE INDEX IF NOT EXISTS ux_loans_one_open_loan_per_item
    ON loans (item_id) WHERE status <> 'Returned';
CREATE INDEX IF NOT EXISTS ix_loans_borrower ON loans (borrower_id);
CREATE INDEX IF NOT EXISTS ix_loans_open_due_date ON loans (due_date) WHERE status <> 'Returned';
CREATE INDEX IF NOT EXISTS ix_items_status ON physical_items (status);
CREATE INDEX IF NOT EXISTS ix_items_category ON physical_items (category);
CREATE INDEX IF NOT EXISTS ix_item_levels_level ON physical_item_levels (education_level);

-- Local index of PDFs. file_path is relative to the application folder, for example storage/digital/...
-- K-12 Module Hub holds Elementary, JHS and SHS modules. College Repository holds College open e-books.
-- source and license give the attribution that the content licensing rules require.
CREATE TABLE IF NOT EXISTS digital_resources (
    resource_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    title             TEXT    NOT NULL,
    category_tab      TEXT    NOT NULL CHECK (category_tab IN ('K-12 Module Hub', 'College Repository')),
    subject           TEXT    NOT NULL,
    education_level   TEXT    NOT NULL CHECK (education_level IN ('Elementary', 'JHS', 'SHS', 'College')),
    file_path         TEXT    NOT NULL UNIQUE,
    file_size_mb      REAL    NOT NULL CHECK (file_size_mb >= 0),
    is_cached_offline INTEGER NOT NULL DEFAULT 0 CHECK (is_cached_offline IN (0, 1)),
    source            TEXT    NOT NULL,
    license           TEXT    NOT NULL,
    CHECK ((category_tab = 'College Repository') = (education_level = 'College'))
);

-- One row per digital download. Needed for the dashboard "modules downloaded" count and the impact report.
CREATE TABLE IF NOT EXISTS digital_downloads (
    download_id   INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id       INTEGER NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    resource_id   INTEGER NOT NULL REFERENCES digital_resources (resource_id) ON DELETE CASCADE,
    downloaded_at TEXT    NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS ix_downloads_user ON digital_downloads (user_id);
CREATE INDEX IF NOT EXISTS ix_downloads_resource ON digital_downloads (resource_id);

-- Library Rental Requests. The library posts what it needs to rent for a short time, for example a projector
-- for a workshop, and the fee it pays per item. Students 18 or older answer with an offer of their own item.
-- The age rule is enforced by RentalService and checked against the school ID at the counter. Offered items never enter physical_items,
-- because they belong to the student and are not lent to anyone else.
-- Request lifecycle: Open, then Filled (enough offers approved, or offers closed with at least one approved),
-- then Completed (every checked-in item handed back). Open becomes Expired when offers close with none approved.
-- Staff can cancel any request that is not Completed.
CREATE TABLE IF NOT EXISTS rental_requests (
    request_id      INTEGER PRIMARY KEY AUTOINCREMENT,
    created_by      INTEGER NOT NULL REFERENCES users (user_id) ON DELETE RESTRICT,
    title           TEXT    NOT NULL,
    description     TEXT    NOT NULL,
    purpose         TEXT    NOT NULL,
    category        TEXT    NOT NULL CHECK (category IN ('Tech', 'Equipment', 'Tools', 'Display', 'Books & Media', 'Other')),
    quantity_needed INTEGER NOT NULL DEFAULT 1 CHECK (quantity_needed BETWEEN 1 AND 20),
    min_condition   TEXT    NOT NULL DEFAULT 'Fair' CHECK (min_condition IN ('Like New', 'Good', 'Fair')),
    offers_close_on TEXT    NOT NULL,
    needed_from     TEXT    NOT NULL,
    needed_until    TEXT    NOT NULL,
    rental_fee      NUMERIC(10, 2) NOT NULL CHECK (rental_fee > 0),
    dropoff_point   TEXT    NOT NULL,
    status          TEXT    NOT NULL DEFAULT 'Open'
                    CHECK (status IN ('Open', 'Filled', 'Completed', 'Cancelled', 'Expired')),
    created_at      TEXT    NOT NULL DEFAULT (datetime('now')),
    CHECK (offers_close_on <= needed_from),
    CHECK (needed_until >= needed_from)
);

-- One student's answer to a request. rental_fee is per item for the whole period, and agreed_fee copies it
-- when staff approve the offer. condition_in and condition_out are recorded at the counter.
-- Offer lifecycle: Offered, Approved, Checked-In, Returned. Side exits: Declined (staff, or automatic when the
-- request fills, closes or is cancelled), Withdrawn (the student, before check-in), Cancelled (request cancelled
-- after approval). A checked-in item always ends as Returned.
CREATE TABLE IF NOT EXISTS rental_offers (
    offer_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    request_id     INTEGER NOT NULL REFERENCES rental_requests (request_id) ON DELETE RESTRICT,
    owner_id       INTEGER NOT NULL REFERENCES users (user_id) ON DELETE RESTRICT,
    item_name      TEXT    NOT NULL,
    brand_model    TEXT,
    description    TEXT,
    condition      TEXT    NOT NULL CHECK (condition IN ('Like New', 'Good', 'Fair')),
    photo_path     TEXT,
    owner_note     TEXT,
    status         TEXT    NOT NULL DEFAULT 'Offered'
                   CHECK (status IN ('Offered', 'Approved', 'Checked-In', 'Returned', 'Declined', 'Withdrawn', 'Cancelled')),
    agreed_fee     NUMERIC(10, 2) CHECK (agreed_fee >= 0),
    fee_paid       INTEGER NOT NULL DEFAULT 0 CHECK (fee_paid IN (0, 1)),
    decided_by     INTEGER REFERENCES users (user_id),
    decline_reason TEXT,
    condition_in   TEXT    CHECK (condition_in IN ('Like New', 'Good', 'Fair')),
    condition_out  TEXT    CHECK (condition_out IN ('Like New', 'Good', 'Fair')),
    damage_note    TEXT,
    offered_at     TEXT    NOT NULL DEFAULT (datetime('now')),
    decided_at     TEXT,
    checked_in_at  TEXT,
    returned_at    TEXT,
    CHECK ((status IN ('Checked-In', 'Returned')) = (checked_in_at IS NOT NULL)),
    CHECK ((status = 'Returned') = (returned_at IS NOT NULL))
);

-- One live offer per student per request. The database refuses a second one, not only the UI.
CREATE UNIQUE INDEX IF NOT EXISTS ux_offers_one_live_per_owner
    ON rental_offers (request_id, owner_id) WHERE status IN ('Offered', 'Approved', 'Checked-In');
CREATE INDEX IF NOT EXISTS ix_requests_status ON rental_requests (status);
CREATE INDEX IF NOT EXISTS ix_offers_request ON rental_offers (request_id);
CREATE INDEX IF NOT EXISTS ix_offers_owner ON rental_offers (owner_id);

-- Guardian consent for learners under 18 (RA 10173, the Data Privacy Act). Elementary and Junior High students
-- always need it, and Senior High or College students who say they are under 18 too. A row here also marks the
-- account as a minor, so the student cannot offer items for Library Rental Requests.
CREATE TABLE IF NOT EXISTS guardian_consents (
    user_id        INTEGER PRIMARY KEY REFERENCES users (user_id) ON DELETE CASCADE,
    guardian_name  TEXT    NOT NULL,
    relationship   TEXT    NOT NULL,
    contact_number TEXT    NOT NULL,
    consented_at   TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- Campus announcements posted by library staff. Shown on the dashboard until expires_on (inclusive)
-- or until staff remove them.
CREATE TABLE IF NOT EXISTS announcements (
    announcement_id INTEGER PRIMARY KEY AUTOINCREMENT,
    posted_by       INTEGER NOT NULL REFERENCES users (user_id) ON DELETE RESTRICT,
    title           TEXT    NOT NULL,
    body            TEXT    NOT NULL,
    posted_on       TEXT    NOT NULL,
    expires_on      TEXT,
    removed         INTEGER NOT NULL DEFAULT 0 CHECK (removed IN (0, 1)),
    CHECK (expires_on IS NULL OR expires_on >= posted_on)
);

-- One saved, unsent donation form per student ("Save draft"). levels is a comma-separated list of levels.
CREATE TABLE IF NOT EXISTS donation_drafts (
    user_id       INTEGER PRIMARY KEY REFERENCES users (user_id) ON DELETE CASCADE,
    category      TEXT,
    title         TEXT,
    brand_model   TEXT,
    condition     TEXT,
    levels        TEXT,
    dropoff_point TEXT,
    photo_path    TEXT,
    saved_at      TEXT    NOT NULL DEFAULT (datetime('now', 'localtime'))
);

-- Student Marketplace: peer-to-peer lending between students. The library is not involved in
-- any of this, so these tables are separate from physical_items, loans and rental_offers.
CREATE TABLE IF NOT EXISTS marketplace_listings (
    listing_id      INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_id        INTEGER NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    title           TEXT    NOT NULL,
    description     TEXT    NOT NULL,
    category        TEXT    NOT NULL CHECK (category IN ('Calculators','Laptops & Tablets','Textbooks','Lab/Study Kits','Other')),
    condition       TEXT    NOT NULL CHECK (condition IN ('Like New','Good','Fair')),
    education_level TEXT    NOT NULL CHECK (education_level IN ('Elementary','JHS','SHS','College')),
    daily_fee       NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (daily_fee >= 0),
    pickup_point    TEXT    NOT NULL,
    photo_path      TEXT,
    status          TEXT    NOT NULL DEFAULT 'Active' CHECK (status IN ('Active','Paused','Removed')),
    created_at      TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS marketplace_borrows (
    borrow_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    listing_id    INTEGER NOT NULL REFERENCES marketplace_listings (listing_id) ON DELETE CASCADE,
    borrower_id   INTEGER NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    owner_id      INTEGER NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    status        TEXT    NOT NULL DEFAULT 'Requested'
                  CHECK (status IN ('Requested','Accepted','Borrowed','Returned','Declined','Cancelled')),
    agreed_fee    NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (agreed_fee >= 0),
    note          TEXT,
    requested_at  TEXT    NOT NULL DEFAULT (datetime('now')),
    responded_at  TEXT,
    borrowed_at   TEXT,
    returned_at   TEXT,
    CHECK (owner_id <> borrower_id),
    CHECK ((status = 'Returned') = (returned_at IS NOT NULL)),
    CHECK ((status = 'Borrowed') = (borrowed_at IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS marketplace_messages (
    message_id  INTEGER PRIMARY KEY AUTOINCREMENT,
    listing_id  INTEGER NOT NULL REFERENCES marketplace_listings (listing_id) ON DELETE CASCADE,
    sender_id   INTEGER NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    body        TEXT    NOT NULL,
    sent_at     TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_marketplace_one_live_borrow_per_listing
    ON marketplace_borrows (listing_id) WHERE status IN ('Requested','Accepted','Borrowed');
CREATE UNIQUE INDEX IF NOT EXISTS ux_marketplace_one_live_borrow_per_borrower
    ON marketplace_borrows (listing_id, borrower_id) WHERE status IN ('Requested','Accepted','Borrowed');
CREATE INDEX IF NOT EXISTS ix_marketplace_listings_owner ON marketplace_listings (owner_id);
CREATE INDEX IF NOT EXISTS ix_marketplace_listings_status ON marketplace_listings (status);
CREATE INDEX IF NOT EXISTS ix_marketplace_borrows_listing ON marketplace_borrows (listing_id);
CREATE INDEX IF NOT EXISTS ix_marketplace_borrows_borrower ON marketplace_borrows (borrower_id);
CREATE INDEX IF NOT EXISTS ix_marketplace_borrows_owner ON marketplace_borrows (owner_id);
CREATE INDEX IF NOT EXISTS ix_marketplace_messages_listing ON marketplace_messages (listing_id);

-- Password reset requests submitted by students when they click "Forgot password?".
CREATE TABLE IF NOT EXISTS password_reset_requests (
    request_id   INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id      INTEGER NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    status       TEXT    NOT NULL DEFAULT 'Pending' CHECK (status IN ('Pending', 'Approved', 'Rejected')),
    requested_at TEXT    NOT NULL DEFAULT (datetime('now')),
    resolved_by  INTEGER REFERENCES users (user_id),
    resolved_at  TEXT
);
CREATE INDEX IF NOT EXISTS ix_password_resets_status ON password_reset_requests (status);


