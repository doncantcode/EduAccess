-- EduAccess demo seed data. DEVELOPMENT ONLY.
-- Loaded by DatabaseInitializer only when started with demo seed on and the users table is empty.
-- Demo students and the demo admin LIB-0001 use the password Demo#2026.
-- TEST ACCOUNTS (exceptions to the 8-character password rule, for testing only):
--   test-student  password 123        (student)
--   123           password admin      (library admin)
-- Remove the test accounts and change the demo passwords before any real deployment.
-- Loan and download dates are relative to date('now', 'localtime'), so the overdue and due-soon states stay realistic.
-- Statements are split on the semicolon character, so no value below may contain one.

INSERT INTO users (user_id, school_id, password_hash, full_name, role, intention, education_level, program, year_level, campus) VALUES
(1,  'LIB-0001',      'pbkdf2_sha256$600000$NqBFXD4+vk9Memj/rCdD4w==$05uEr+UX+qrBAx2MB8P+lNAaPMqi+6eymJ/lt/EcKPU=', 'Ana Dela Cruz',   'Admin',   NULL,      NULL,         NULL,                        NULL, 'Main Campus'),
(3,  '2026-EL-0001',  'pbkdf2_sha256$600000$3CwJ4tIb8yY8/XfsGryf3w==$vxmWyb7KNyPd+JzkJKGRFIrTNCEXO+YsJ5v0m0Th6Vc=', 'Mia Santos',       'Student', 'Borrower', 'Elementary', NULL,                        4,    'Main Campus'),
(4,  '2026-EL-0002',  'pbkdf2_sha256$600000$VylN9WPRb6fQh3fKgm1QOQ==$LzLBA1JpaMP7k2DckMc6Fqdh5CkzZEzFI6mTWF9yHoc=', 'Lucas Garcia',     'Student', 'Borrower', 'Elementary', NULL,                        6,    'Main Campus'),
(5,  '2026-JHS-0001', 'pbkdf2_sha256$600000$hXXPCsaPguQGVv3qSu0pKw==$96dfEW+xoCIMWEx0PR8MzxD2AbCc2AEHiuzmBpTIw4Y=', 'Ethan Bautista',   'Student', 'Borrower', 'JHS',        NULL,                        8,    'Main Campus'),
(6,  '2026-JHS-0002', 'pbkdf2_sha256$600000$b9Luwi7xWRNDg4vXUEWbyA==$i9sMUVBQ7DRA7XXOKtIRf81wP4uWgsFcKziavJczf/Q=', 'Sofia Mendoza',    'Student', 'Both',     'JHS',        NULL,                        10,   'North Campus'),
(7,  '2026-SHS-0001', 'pbkdf2_sha256$600000$gTuXS4OwH4sd3+lHp9f1hQ==$BxkYiIjlDvDnkqO1mS2pEVQRFZFxEskXtYCodXhAS8E=', 'Jacob Cruz',       'Student', 'Borrower', 'SHS',        'STEM',                      11,   'Main Campus'),
(8,  '2026-SHS-0002', 'pbkdf2_sha256$600000$T3zhcuBJkx8tZZgyLqMddQ==$Yqt6vhVI4/3cI+bkJ7Zrp4oKri/009vug2fbdXeWsS0=', 'Ava Villanueva',   'Student', 'Donor',    'SHS',        'HUMSS',                     12,   'Main Campus'),
(9,  '2026-SHS-0003', 'pbkdf2_sha256$600000$JhAElz7nojo/gwOAUb8rZg==$WuP7bVlP4vY9stzwmQ+PbDL/zvYMZW3+6CmO9h7kGMk=', 'Noah Aquino',      'Student', 'Borrower', 'SHS',        'TVL-ICT',                   11,   'North Campus'),
(10, '2026-COL-0001', 'pbkdf2_sha256$600000$SLHHSIWW1b4CMy17ErePxg==$2Z2X829RIXBQS9ZZTkpS/mvlfpstbrSnlcYHyGXReaM=', 'Isabella Ramos',  'Student', 'Borrower', 'College',    'BS Computer Science',       2,    'Main Campus'),
(11, '2026-COL-0002', 'pbkdf2_sha256$600000$JhUqUxmlABM3CoA8jXTKuA==$BEnWkw44xcOXvKom26oRn+RllKLbUUHQRlPAjc+G2l0=', 'Gabriel Lim',      'Student', 'Both',     'College',    'BS Education',              3,    'North Campus'),
(12, '2026-COL-0003', 'pbkdf2_sha256$600000$wyV+4T8QCxDJ3IRiQjyImw==$dZKjv8V0Ivnrh1Ftm1KaEvsyHhBNBcI2WLbEQO1Z0qs=', 'Chloe Tan',        'Student', 'Donor',    'College',    'BS Business Administration', 1,    'Main Campus'),
(13, 'DON-0001',      'pbkdf2_sha256$600000$pohkfQY5zaFDyK4Ky54AUQ==$mcFYdwCAWHS60Jn8oHHRT9CsUKzvTPm31OqNzOd2/Gc=', 'Carlos Navarro',   'Student', 'Donor',    'College',    'BS Engineering',            4,    'Main Campus');

-- TEST ONLY: student test account. Password 123. Exception to the 8-character password rule.
-- TEST ONLY: admin test account, school ID 123, password admin. Exception to the same rule.
INSERT INTO users (user_id, school_id, password_hash, full_name, role, intention, education_level, program, year_level, campus) VALUES
(14, 'test-student', 'pbkdf2_sha256$600000$uSVEMFnMfQR4B/L1w+Wsew==$b2R+bkTciXgGT+dJS+7M5tK7LSUso82puEIXF5Xl/ek=', 'Test Student', 'Student', 'Both', 'College', 'BS Computer Science', 2, 'Main Campus'),
(15, '123',          'pbkdf2_sha256$600000$aPlvbnI2aFcMM5IOgBM4wQ==$xt5mr5jK2OjLan94H7AwJARQXMwjI+GEPwTcXb4R0dA=', 'Test Admin',    'Admin',   NULL,   NULL,      NULL,                        NULL, 'Main Campus');

-- donor_id NULL means library-owned stock.
INSERT INTO physical_items (item_id, donor_id, title, brand_model, category, condition, pickup_point, status, type_of_giving) VALUES
(1,  13,   'Scientific Calculator',               'Casio fx-991EX',                    'Calculators',       'Good',     'Library Counter A', 'Borrowed',  'Lend'),
(2,  NULL, 'Scientific Calculator',               'Casio fx-82MS',                     'Calculators',       'Like New', 'Library Counter A', 'Overdue',   'Donate'),
(3,  13,   'Graphing Calculator',                 'Texas Instruments TI-84 Plus',      'Calculators',       'Good',     'Library Counter A', 'Available', 'Lend'),
(4,  12,   'Basic Calculator',                    'Generic 12-digit desktop',          'Calculators',       'Fair',     'Library Counter A', 'Overdue',   'Donate'),
(5,  13,   'Student Laptop',                      'Lenovo 300e Chromebook',            'Laptops & Tablets', 'Good',     'Library Counter B', 'Borrowed',  'Lend'),
(6,  8,    'Tablet',                              'Samsung Galaxy Tab A7',             'Laptops & Tablets', 'Fair',     'Admin Office',      'Pending',   'Donate'),
(7,  12,   'Laptop',                              'Acer Aspire 3',                     'Laptops & Tablets', 'Like New', 'Library Counter B', 'Pending',   'Donate'),
(8,  NULL, 'Grade 4 Math Learner Module Set',     'Revised edition',                   'Textbooks',         'Good',     'Library Counter A', 'Available', 'Donate'),
(9,  13,   'Grade 9 Science Textbook',            'Revised edition',                   'Textbooks',         'Fair',     'Library Counter A', 'Returned',  'Donate'),
(10, 12,   'Introduction to Programming (Print)', '2nd edition',                       'Textbooks',         'Good',     'Library Counter B', 'Returned',  'Donate'),
(11, 8,    'Chemistry Lab Kit',                   'Basic titration set',               'Lab/Study Kits',    'Good',     'Library Counter A', 'Available', 'Lend'),
(12, NULL, 'Geometry Kit',                        'Protractor, compass and ruler set', 'Lab/Study Kits',    'Like New', 'Library Counter A', 'Returned',  'Donate'),
(13, 8,    'Physics Lab Kit',                     'Force and motion set',              'Lab/Study Kits',    'Fair',     'Admin Office',      'Rejected',  'Donate'),
(14, 12,   'Engineering Drafting Kit',            'Drafting set with compass',         'Lab/Study Kits',    'Good',     'Library Counter B', 'Pending',   'Lend');

-- Several levels per item, matching the "Recommended for" checkboxes on the donation form.
INSERT INTO physical_item_levels (item_id, education_level) VALUES
(1,  'SHS'),
(1,  'College'),
(2,  'JHS'),
(3,  'SHS'),
(3,  'College'),
(4,  'Elementary'),
(5,  'SHS'),
(5,  'College'),
(6,  'JHS'),
(6,  'SHS'),
(7,  'College'),
(8,  'Elementary'),
(9,  'JHS'),
(10, 'College'),
(11, 'SHS'),
(12, 'JHS'),
(12, 'SHS'),
(13, 'SHS'),
(13, 'College'),
(14, 'College');

-- Open loans (Active or Overdue) point at items whose status is Borrowed or Overdue.
-- Loan 2 has used its one extension (14 + 3 days). Loan 4 is an equity-program waiver.
INSERT INTO loans (loan_id, borrower_id, item_id, borrow_date, due_date, return_date, loan_period_days, extension_count, fee_amount, fee_waived, status) VALUES
(1, 7,  1,  date('now', 'localtime', '-2 days'),  date('now', 'localtime', '+5 days'),  NULL,                                                       7,  0, 0.00,  0, 'Active'),
(2, 10, 5,  date('now', 'localtime', '-10 days'), date('now', 'localtime', '+7 days'),  NULL,                                                       14, 1, 0.00,  0, 'Active'),
(3, 5,  2,  date('now', 'localtime', '-12 days'), date('now', 'localtime', '-5 days'), NULL,                                                       7,  0, 25.00, 0, 'Overdue'),
(4, 3,  4,  date('now', 'localtime', '-20 days'), date('now', 'localtime', '-6 days'), NULL,                                                       14, 0, 0.00, 1, 'Overdue'),
(5, 6,  9,  date('now', 'localtime', '-30 days'), date('now', 'localtime', '-23 days'), date('now', 'localtime', '-20 days'),                      7,  0, 15.00, 0, 'Returned'),
(6, 11, 10, date('now', 'localtime', '-40 days'), date('now', 'localtime', '-26 days'), date('now', 'localtime', '-28 days'),                      14, 0, 0.00,  0, 'Returned'),
(7, 9,  12, date('now', 'localtime', '-15 days'), date('now', 'localtime', '-12 days'), date('now', 'localtime', '-13 days'),                      3,  0, 0.00,  0, 'Returned');

-- file_size_mb is the real size of each placeholder PDF in storage/digital (under 1 KB each).
-- source and license are placeholders. Replace them with the real attribution before any content is shared.
INSERT INTO digital_resources (resource_id, title, category_tab, subject, education_level, file_path, file_size_mb, is_cached_offline, source, license) VALUES
(1,  'Grade 1 Math: Counting to 100 (Sample)',              'K-12 Module Hub',    'Mathematics',     'Elementary', 'storage/digital/k12/elementary/grade1-math-counting.pdf',         0.0008, 1, 'Demo placeholder', 'Replace before use'),
(2,  'Grade 4 Science: Living Things (Sample)',              'K-12 Module Hub',    'Science',         'Elementary', 'storage/digital/k12/elementary/grade4-science-living-things.pdf',  0.0008, 0, 'Demo placeholder', 'Replace before use'),
(3,  'Grade 6 English: Reading Comprehension (Sample)',     'K-12 Module Hub',    'English',         'Elementary', 'storage/digital/k12/elementary/grade6-english-reading.pdf',        0.0008, 1, 'Demo placeholder', 'Replace before use'),
(4,  'Grade 7 Mathematics: Integers (Sample)',              'K-12 Module Hub',    'Mathematics',     'JHS',        'storage/digital/k12/jhs/grade7-math-integers.pdf',                0.0008, 0, 'Demo placeholder', 'Replace before use'),
(5,  'Grade 9 Science: Chemical Reactions (Sample)',        'K-12 Module Hub',    'Science',         'JHS',        'storage/digital/k12/jhs/grade9-science-chemical-reactions.pdf',   0.0008, 1, 'Demo placeholder', 'Replace before use'),
(6,  'Grade 10 Araling Panlipunan: Globalization (Sample)', 'K-12 Module Hub',    'Social Studies',  'JHS',        'storage/digital/k12/jhs/grade10-ap-globalization.pdf',            0.0008, 0, 'Demo placeholder', 'Replace before use'),
(7,  'General Mathematics: Functions (Sample)',             'K-12 Module Hub',    'Mathematics',     'SHS',        'storage/digital/k12/shs/general-math-functions.pdf',              0.0008, 1, 'Demo placeholder', 'Replace before use'),
(8,  'Earth and Life Science: Plate Tectonics (Sample)',    'K-12 Module Hub',    'Science',         'SHS',        'storage/digital/k12/shs/earth-science-plate-tectonics.pdf',       0.0008, 0, 'Demo placeholder', 'Replace before use'),
(9,  'Creative Writing (HUMSS): Poetry Forms (Sample)',     'K-12 Module Hub',    'English',         'SHS',        'storage/digital/k12/shs/humss-creative-writing-poetry.pdf',       0.0008, 0, 'Demo placeholder', 'Replace before use'),
(10, 'Introduction to Programming (Sample Chapter)',        'College Repository', 'Computer Science', 'College',    'storage/digital/college/intro-programming-sample.pdf',            0.0008, 1, 'Demo placeholder', 'Replace before use'),
(11, 'Principles of Accounting (Sample Chapter)',           'College Repository', 'Accounting',      'College',    'storage/digital/college/principles-accounting-sample.pdf',        0.0008, 0, 'Demo placeholder', 'Replace before use'),
(12, 'General Psychology (Sample Chapter)',                 'College Repository', 'Psychology',      'College',    'storage/digital/college/general-psychology-sample.pdf',           0.0008, 0, 'Demo placeholder', 'Replace before use');

INSERT INTO digital_downloads (user_id, resource_id, downloaded_at) VALUES
(3,  1,  datetime('now', 'localtime', '-3 days')),
(3,  3,  datetime('now', 'localtime', '-1 days')),
(5,  4,  datetime('now', 'localtime', '-2 days')),
(6,  5,  datetime('now', 'localtime', '-20 days')),
(6,  4,  datetime('now', 'localtime', '-9 days')),
(7,  7,  datetime('now', 'localtime', '-5 days')),
(7,  8,  datetime('now', 'localtime', '-2 days')),
(9,  9,  datetime('now', 'localtime', '-7 days')),
(10, 10, datetime('now', 'localtime', '-4 days')),
(10, 11, datetime('now', 'localtime', '-1 days')),
(11, 10, datetime('now', 'localtime', '-30 days')),
(11, 12, datetime('now', 'localtime', '-3 days'));

-- More reading material for the Physical Library tab (Textbooks are listed there, equipment in the Physical Hub).
INSERT INTO physical_items (item_id, donor_id, title, brand_model, category, condition, pickup_point, status, type_of_giving) VALUES
(15, NULL, 'Noli Me Tangere (Filipino translation)',  'Adarna House edition',        'Textbooks', 'Good',     'Library Counter A', 'Available', 'Donate'),
(16, 12,   'General Chemistry 1 Reviewer',            'SHS STEM, 2023 printing',     'Textbooks', 'Like New', 'Library Counter A', 'Available', 'Donate'),
(17, NULL, 'Calculus: Early Transcendentals',         '8th edition',                 'Textbooks', 'Good',     'Library Counter B', 'Available', 'Donate'),
(18, 8,    'Grade 7 English Learner Module Set',      'Quarters 1 to 4',             'Textbooks', 'Fair',     'Library Counter A', 'Available', 'Donate'),
(19, NULL, 'Philippine History Atlas',                'Illustrated, hardcover',      'Textbooks', 'Good',     'Library Counter A', 'Available', 'Donate'),
(20, 13,   'Java Programming (Print)',                '11th edition',                'Textbooks', 'Good',     'Library Counter B', 'Available', 'Donate');

INSERT INTO physical_item_levels (item_id, education_level) VALUES
(15, 'JHS'),
(15, 'SHS'),
(16, 'SHS'),
(17, 'SHS'),
(17, 'College'),
(18, 'JHS'),
(19, 'Elementary'),
(19, 'JHS'),
(20, 'College');

-- Library Rental Requests. Dates are relative to today, so the board always shows open requests.
-- created_by 1 is the demo admin. rental_fee is per item for the whole rental, 0 means unpaid.
INSERT INTO rental_requests (request_id, created_by, title, description, purpose, category, quantity_needed, min_condition, offers_close_on, needed_from, needed_until, rental_fee, dropoff_point, status) VALUES
(1, 1, 'Portable Projector',            'HDMI input and at least 2000 lumens. A carry bag is a plus.',                 'Science Fair 2026 project demos',      'Tech',          2, 'Good', date('now', 'localtime', '+3 days'),  date('now', 'localtime', '+6 days'),  date('now', 'localtime', '+9 days'),  150.00, 'Library Counter A', 'Open'),
(2, 1, 'Bluetooth Speaker',             'Loud enough for a 40-seat reading room. Battery or plug-in is fine.',         'Spoken poetry night',    'Equipment',     1, 'Fair', date('now', 'localtime', '+2 days'),  date('now', 'localtime', '+4 days'),  date('now', 'localtime', '+4 days'),  50.00,  'Library Counter B', 'Open'),
(3, 1, 'Tabletop Display Easels',       'Wood or metal easels that hold A3 posters upright.',                          'Book Week poster exhibit',             'Display',       4, 'Fair', date('now', 'localtime', '+5 days'),  date('now', 'localtime', '+7 days'),  date('now', 'localtime', '+14 days'), 0.00,   'Library Counter A', 'Open'),
(4, 1, 'Digital Camera with Tripod',    'Any camera with a tripod mount. We will photograph old yearbooks.',           'Library archive digitization day',     'Tech',          1, 'Good', date('now', 'localtime', '+1 days'),  date('now', 'localtime', '+2 days'),  date('now', 'localtime', '+3 days'),  300.00, 'Admin Office',      'Open'),
(5, 1, 'Cordless Drill',                'With screwdriver bits.',                                                      'Reading-nook shelf assembly',          'Tools',         1, 'Fair', date('now', 'localtime', '-8 days'),  date('now', 'localtime', '-6 days'),  date('now', 'localtime', '-5 days'),  100.00, 'Library Counter B', 'Completed'),
(6, 1, 'Classic Filipino Novels',       'Noli Me Tangere or El Filibusterismo, any edition, for a display shelf.',     'Rizal reading circle',                 'Books & Media', 3, 'Fair', date('now', 'localtime', '+6 days'),  date('now', 'localtime', '+8 days'),  date('now', 'localtime', '+12 days'), 0.00,   'Library Counter A', 'Open');

INSERT INTO rental_offers (offer_id, request_id, owner_id, item_name, brand_model, description, condition, owner_note, status, agreed_fee, fee_paid, decided_by, decided_at, condition_in, condition_out, checked_in_at, returned_at) VALUES
(1, 1, 11, 'Projector',         'Epson EB-X51',     'With HDMI cable and remote.',      'Good',     NULL,                         'Approved', 150.00, 0, 1,    datetime('now', 'localtime', '-1 days'), NULL,   NULL,   NULL,                                     NULL),
(2, 1, 6,  'Mini projector',    'BenQ MS560',       'Comes with a carry bag.',          'Like New', 'I can drop it off after class.', 'Offered',  NULL,   0, NULL, NULL,                                    NULL,   NULL,   NULL,                                     NULL),
(3, 2, 14, 'Bluetooth speaker', 'JBL Flip 5',       'Fully charged, with USB-C cable.', 'Good',     NULL,                         'Offered',  NULL,   0, NULL, NULL,                                    NULL,   NULL,   NULL,                                     NULL),
(4, 5, 13, 'Cordless drill',    'Bosch GSR 120-LI', 'Two batteries and a bit set.',     'Good',     NULL,                         'Returned', 100.00, 1, 1,    datetime('now', 'localtime', '-9 days'), 'Good', 'Good', datetime('now', 'localtime', '-6 days'), datetime('now', 'localtime', '-5 days'));
