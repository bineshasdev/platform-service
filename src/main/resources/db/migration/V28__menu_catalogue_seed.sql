-- ============================================================
-- V28 — Predefined menu catalogue (Stage 8d)
-- Generated from Docs/design/menu-catalogue.md v1.1: 305 items across
-- PROVIDER_CONSOLE, PATIENT_PORTAL, KIOSK and PLATFORM_ADMIN.
--   * phase P1 -> is_active = true; P2..P4 are seeded disabled and switched on when they ship
--     (metadata.phase), decision C2 / E3
--   * tier -> plan access: Free = all plans, Starter = starter+, Pro = pro+, Paid = enterprise (E4)
--   * tenant_types from the Tenants column (NULL = every type)
--   * INNER and TAB items are is_visible = false: they are in the catalogue but not in navigation
--   * role access = every system role that already holds the item's privilege (ungated items: every
--     role of the application). Platform Admin and Kiosk items carry no role grant: operators
--     see everything, and the kiosk device identity is not modelled yet.
-- The six patient-portal items re-keyed by V27 are updated in place; everything else is inserted.
-- ============================================================

CREATE TEMP TABLE seed_menu (
    app         TEXT, code TEXT, parent_code TEXT, label TEXT, description TEXT, icon TEXT, route TEXT,
    type        TEXT, behavior TEXT, placement TEXT, privilege TEXT, tier TEXT, tenant_types TEXT[],
    phase       TEXT, module TEXT, is_active BOOLEAN, is_visible BOOLEAN, sort_order INT
) ON COMMIT DROP;

INSERT INTO seed_menu VALUES
    ('PROVIDER_CONSOLE', 'home', NULL, 'Home', 'Role-aware landing: today''s appointments, inbox counts, alerts, KPIs', 'LayoutDashboard', '/dashboard', 'LINK', 'NAVIGATE', 'LEFT', 'dashboard.view', 'Free', NULL, 'P1', 'M14', true, true, 10),
    ('PROVIDER_CONSOLE', 'schedule', NULL, 'Schedule', 'Day/week calendar by provider, room, location; book, move, cancel', 'CalendarDays', '/schedule', 'LINK', 'NAVIGATE', 'LEFT', 'appointment.view', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', 'M03', false, true, 20),
    ('PROVIDER_CONSOLE', 'schedule.queue', 'schedule', 'Queue', 'Walk-in queue, tokens, check-in, now-serving', 'ListOrdered', '/schedule/queue', 'LINK', 'NAVIGATE', 'LEFT', 'appointment.book', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', 'M03', false, true, 10),
    ('PROVIDER_CONSOLE', 'schedule.waitlist', 'schedule', 'Waitlist', 'Waitlist and automatic backfill offers', 'Hourglass', '/schedule/waitlist', 'LINK', 'NAVIGATE', 'LEFT', 'appointment.book', 'Starter', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', 'M03', false, true, 20),
    ('PROVIDER_CONSOLE', 'patients', NULL, 'Patients', 'Search and list patients; register; recent', 'Users', '/patients', 'LINK', 'NAVIGATE', 'LEFT', 'patient.view', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P1', 'M02', true, true, 30),
    ('PROVIDER_CONSOLE', 'patients.duplicates', 'patients', 'Duplicate review', 'Probable-match review queue; merge / un-merge', 'GitMerge', '/patients/duplicates', 'LINK', 'NAVIGATE', 'LEFT', 'patient.merge', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P1', 'M02', true, true, 10),
    ('PROVIDER_CONSOLE', 'encounters', NULL, 'Encounters', 'My open, unsigned and recent encounters', 'Stethoscope', '/encounters', 'LINK', 'NAVIGATE', 'LEFT', 'encounter.view', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', 'M04', false, true, 40),
    ('PROVIDER_CONSOLE', 'inbox', NULL, 'Inbox', 'Everything waiting for me (see §3.4 tabs)', 'Inbox', '/inbox', 'LINK', 'NAVIGATE', 'LEFT', 'encounter.view', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', 'M04/M06', false, true, 50),
    ('PROVIDER_CONSOLE', 'orders', NULL, 'Orders & results', 'Track lab/imaging orders through their lifecycle; result review', 'FlaskConical', '/orders', 'LINK', 'NAVIGATE', 'LEFT', 'lab.order', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', 'M06', false, true, 60),
    ('PROVIDER_CONSOLE', 'telehealth', NULL, 'Telehealth', 'Virtual waiting room, today''s consults, admit patient', 'Video', '/telehealth', 'LINK', 'NAVIGATE', 'LEFT', 'telehealth.consult', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P4', 'M17', false, true, 70),
    ('PROVIDER_CONSOLE', 'monitoring', NULL, 'Remote monitoring', 'Monitored chronic patients, threshold alerts, device readings', 'HeartPulse', '/monitoring', 'LINK', 'NAVIGATE', 'LEFT', 'vitals.record', 'Pro', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P4', 'M07', false, true, 80),
    ('PROVIDER_CONSOLE', 'referrals', NULL, 'Referrals', 'Sent and received referrals, loop status, stalled alerts', 'Send', '/referrals', 'LINK', 'NAVIGATE', 'LEFT', 'referral.view', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P3', 'M13', false, true, 90),
    ('PROVIDER_CONSOLE', 'lab.orders', NULL, 'Order intake', 'Incoming orders (network + walk-in); accept, quote price/TAT', 'ClipboardList', '/lab/orders', 'LINK', 'NAVIGATE', 'LEFT', 'lab.order', 'Free', ARRAY['LAB']::text[], 'P3', 'M11', false, true, 100),
    ('PROVIDER_CONSOLE', 'lab.samples', NULL, 'Samples', 'Accession, barcode/QR, collection status, rejections', 'TestTube', '/lab/samples', 'LINK', 'NAVIGATE', 'LEFT', 'lab.sample.manage', 'Starter', ARRAY['LAB']::text[], 'P3', 'M11', false, true, 110),
    ('PROVIDER_CONSOLE', 'lab.home_collection', NULL, 'Home collection', 'Book and dispatch phlebotomists; route and status', 'MapPin', '/lab/home-collection', 'LINK', 'NAVIGATE', 'LEFT', 'lab.sample.manage', 'Starter', ARRAY['LAB']::text[], 'P3', 'M11', false, true, 120),
    ('PROVIDER_CONSOLE', 'lab.worklist', NULL, 'Worklist', 'Tests in process by department; enter results', 'ListChecks', '/lab/worklist', 'LINK', 'NAVIGATE', 'LEFT', 'lab.result.upload', 'Free', ARRAY['LAB']::text[], 'P3', 'M11', false, true, 130),
    ('PROVIDER_CONSOLE', 'lab.results', NULL, 'Results', 'Validate, release, bulk/LIS upload, amend', 'FileCheck', '/lab/results', 'LINK', 'NAVIGATE', 'LEFT', 'lab.result.upload', 'Free', ARRAY['LAB']::text[], 'P3', 'M11', false, true, 140),
    ('PROVIDER_CONSOLE', 'lab.tat', NULL, 'TAT monitor', 'SLA board: at-risk and late results', 'Timer', '/lab/tat', 'LINK', 'NAVIGATE', 'LEFT', 'lab.result.upload', 'Pro', ARRAY['LAB']::text[], 'P3', 'M11', false, true, 150),
    ('PROVIDER_CONSOLE', 'pharmacy.queue', NULL, 'Prescription queue', 'Incoming e-prescriptions; accept, verify, start dispensing', 'ClipboardList', '/pharmacy/queue', 'LINK', 'NAVIGATE', 'LEFT', 'prescription.dispense', 'Free', ARRAY['STORE']::text[], 'P3', 'M12', false, true, 160),
    ('PROVIDER_CONSOLE', 'pharmacy.substitutions', NULL, 'Substitutions', 'Proposed generic substitutions and their rule decisions', 'Replace', '/pharmacy/substitutions', 'LINK', 'NAVIGATE', 'LEFT', 'prescription.dispense', 'Starter', ARRAY['STORE']::text[], 'P3', 'M12', false, true, 170),
    ('PROVIDER_CONSOLE', 'pharmacy.inventory', NULL, 'Inventory', 'Stock, batches, expiry, reorder (see tabs)', 'Package', '/pharmacy/inventory', 'LINK', 'NAVIGATE', 'LEFT', 'pharmacy.inventory.manage', 'Starter', ARRAY['STORE']::text[], 'P3', 'M12', false, true, 180),
    ('PROVIDER_CONSOLE', 'pharmacy.register', NULL, 'Controlled register', 'Schedule H/H1/X register; statutory export', 'ShieldAlert', '/pharmacy/register', 'LINK', 'NAVIGATE', 'LEFT', 'prescription.dispense', 'Free', ARRAY['STORE']::text[], 'P3', 'M12', false, true, 190),
    ('PROVIDER_CONSOLE', 'pharmacy.deliveries', NULL, 'Deliveries', 'Delivery and pickup schedule and status', 'Truck', '/pharmacy/deliveries', 'LINK', 'NAVIGATE', 'LEFT', 'prescription.dispense', 'Starter', ARRAY['STORE']::text[], 'P3', 'M12', false, true, 200),
    ('PROVIDER_CONSOLE', 'pharmacy.adherence', NULL, 'Adherence', 'Refill reminders, non-adherence flags', 'Repeat', '/pharmacy/adherence', 'LINK', 'NAVIGATE', 'LEFT', 'prescription.dispense', 'Pro', ARRAY['STORE']::text[], 'P3', 'M12', false, true, 210),
    ('PROVIDER_CONSOLE', 'billing', NULL, 'Billing', NULL, 'Receipt', NULL, 'GROUP', 'EXPAND', 'LEFT', NULL, 'Free', NULL, 'P2', 'M10', false, true, 220),
    ('PROVIDER_CONSOLE', 'billing.invoices', 'billing', 'Invoices', 'Invoices, GST, multi-language print', 'FileText', '/billing/invoices', 'LINK', 'NAVIGATE', 'LEFT', 'invoice.view', 'Free', NULL, 'P2', 'M10', false, true, 10),
    ('PROVIDER_CONSOLE', 'billing.payments', 'billing', 'Payments', 'Collect (UPI/card/cash), receipts, advances', 'Wallet', '/billing/payments', 'LINK', 'NAVIGATE', 'LEFT', 'payment.collect', 'Free', NULL, 'P2', 'M10', false, true, 20),
    ('PROVIDER_CONSOLE', 'billing.day_close', 'billing', 'Day close', 'Daily reconciliation of collected vs expected', 'CalendarCheck', '/billing/day-close', 'LINK', 'NAVIGATE', 'LEFT', 'payment.collect', 'Free', NULL, 'P2', 'M10', false, true, 30),
    ('PROVIDER_CONSOLE', 'billing.adjustments', 'billing', 'Refunds & adjustments', 'Request / approve refunds and adjustments', 'Undo2', '/billing/adjustments', 'LINK', 'NAVIGATE', 'LEFT', 'billing.manage', 'Free', NULL, 'P2', 'M10', false, true, 40),
    ('PROVIDER_CONSOLE', 'billing.collections', 'billing', 'Collections', 'AR aging and collections work queue', 'AlarmClock', '/billing/collections', 'LINK', 'NAVIGATE', 'LEFT', 'invoice.view', 'Pro', NULL, 'P3', 'M10', false, true, 50),
    ('PROVIDER_CONSOLE', 'billing.settlements', 'billing', 'Settlements', 'Network split settlements; lab/pharmacy commissions', 'Split', '/billing/settlements', 'LINK', 'NAVIGATE', 'LEFT', 'billing.view', 'Pro', NULL, 'P3', 'M10/M11', false, true, 60),
    ('PROVIDER_CONSOLE', 'claims', NULL, 'Claims', NULL, 'Landmark', NULL, 'GROUP', 'EXPAND', 'LEFT', NULL, 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P3', 'M09', false, true, 230),
    ('PROVIDER_CONSOLE', 'claims.pmjay', 'claims', 'PMJAY', 'Beneficiary check, package, pre-auth, claim, NHA submission', 'BadgeCheck', '/claims/pmjay', 'LINK', 'NAVIGATE', 'LEFT', 'claim.manage', 'Pro', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P4', 'M09', false, true, 10),
    ('PROVIDER_CONSOLE', 'claims.tpa', 'claims', 'Insurance / TPA', 'TPA claims with required document sets', 'ShieldCheck', '/claims/tpa', 'LINK', 'NAVIGATE', 'LEFT', 'claim.manage', 'Pro', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P4', 'M09', false, true, 20),
    ('PROVIDER_CONSOLE', 'claims.corporate', 'claims', 'Corporate panels', 'Employer panel billing, monthly invoices', 'Building2', '/claims/corporate', 'LINK', 'NAVIGATE', 'LEFT', 'claim.manage', 'Starter', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P3', 'M09', false, true, 30),
    ('PROVIDER_CONSOLE', 'coding', NULL, 'Coding', 'Coder work queue: priority, aging, assignment', 'Tags', '/coding', 'LINK', 'NAVIGATE', 'LEFT', 'coding.assign', 'Pro', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P4', 'M08', false, true, 240),
    ('PROVIDER_CONSOLE', 'network', NULL, 'Directory', 'Find verified specialists, labs, pharmacies (clinical ranking only)', 'Network', '/network', 'LINK', 'NAVIGATE', 'LEFT', 'referral.view', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P3', 'M13/M14', false, true, 250),
    ('PROVIDER_CONSOLE', 'ai_review', NULL, 'AI review', 'Review queues per agent with SLA timers (see tabs)', 'Sparkles', '/ai-review', 'LINK', 'NAVIGATE', 'LEFT', 'ai.use', 'Paid', NULL, 'P4', 'M18', false, true, 260),
    ('PROVIDER_CONSOLE', 'reports', NULL, 'Reports', 'Report library (see §3.4 tabs)', 'ChartColumn', '/reports', 'LINK', 'NAVIGATE', 'LEFT', 'report.view', 'Starter', NULL, 'P2', 'M14', false, true, 270),
    ('PROVIDER_CONSOLE', 'compliance', NULL, 'Compliance', NULL, 'ShieldCheck', NULL, 'GROUP', 'EXPAND', 'LEFT', NULL, 'Free', NULL, 'P1', 'M20', true, true, 280),
    ('PROVIDER_CONSOLE', 'compliance.alerts', 'compliance', 'Alerts', 'Break-glass events, access anomalies, consent violations', 'Siren', '/compliance/alerts', 'LINK', 'NAVIGATE', 'LEFT', 'compliance.monitor', 'Free', NULL, 'P1', 'M20', true, true, 10),
    ('PROVIDER_CONSOLE', 'compliance.audit', 'compliance', 'Audit log', 'Tamper-evident access log; filter, export', 'ScrollText', '/compliance/audit', 'LINK', 'NAVIGATE', 'LEFT', 'audit.view', 'Free', NULL, 'P1', 'M20', true, true, 20),
    ('PROVIDER_CONSOLE', 'compliance.access_reviews', 'compliance', 'Access reviews', 'Quarterly attestation of users, roles, PHI grants', 'UserCheck', '/compliance/access-reviews', 'LINK', 'NAVIGATE', 'LEFT', 'compliance.monitor', 'Pro', NULL, 'P3', 'M01', false, true, 30),
    ('PROVIDER_CONSOLE', 'compliance.data_requests', 'compliance', 'Patient requests', 'Amend / export / erase requests and decisions', 'FileQuestion', '/compliance/data-requests', 'LINK', 'NAVIGATE', 'LEFT', 'consent.manage', 'Free', NULL, 'P1', 'M02', true, true, 40),
    ('PROVIDER_CONSOLE', 'compliance.incidents', 'compliance', 'Incidents', 'Breach workflow with statutory timers', 'AlertTriangle', '/compliance/incidents', 'LINK', 'NAVIGATE', 'LEFT', 'compliance.monitor', 'Free', NULL, 'P1', 'M20', true, true, 50),
    ('PROVIDER_CONSOLE', 'settings', NULL, 'Settings', 'Settings hub (§3.5)', 'Settings', NULL, 'GROUP', 'EXPAND', 'LEFT', NULL, 'Free', NULL, 'P1', 'M01', true, true, 290),
    ('PROVIDER_CONSOLE', 'header.org_switcher', NULL, 'Organisation', 'Lists my organisations; switching re-logs into that org (existing `/select-organization`)', NULL, NULL, 'LINK', 'NAVIGATE', 'HEADER', NULL, 'Free', NULL, 'P1', NULL, true, true, 300),
    ('PROVIDER_CONSOLE', 'header.location', NULL, 'Location', 'Filters schedule, queue, reports by location', NULL, NULL, 'LINK', 'NAVIGATE', 'HEADER', NULL, 'Starter', NULL, 'P2', NULL, false, true, 310),
    ('PROVIDER_CONSOLE', 'header.search', NULL, 'Search', 'Patients, appointments, invoices, orders — results filtered by privilege', NULL, NULL, 'LINK', 'NAVIGATE', 'HEADER', 'patient.view', 'Free', NULL, 'P1', NULL, true, true, 320),
    ('PROVIDER_CONSOLE', 'header.quick', NULL, 'New', 'Opens the quick-create menu (§3.3)', NULL, NULL, 'GROUP', 'EXPAND', 'HEADER', NULL, 'Free', NULL, 'P1', NULL, true, true, 330),
    ('PROVIDER_CONSOLE', 'header.ai_review', NULL, 'AI review', 'Badge with items awaiting my review → `/ai-review`', NULL, NULL, 'LINK', 'NAVIGATE', 'HEADER', 'ai.use', 'Paid', NULL, 'P4', NULL, false, true, 340),
    ('PROVIDER_CONSOLE', 'header.notifications', NULL, 'Notifications', 'Bell dropdown; "See all" → `/notifications`', NULL, NULL, 'LINK', 'NAVIGATE', 'HEADER', NULL, 'Free', NULL, 'P1', NULL, true, true, 350),
    ('PROVIDER_CONSOLE', 'header.help', NULL, 'Help', 'Help centre (external), What''s new (announcements), keyboard shortcuts, contact support', NULL, NULL, 'LINK', 'NAVIGATE', 'HEADER', NULL, 'Free', NULL, 'P1', NULL, true, true, 360),
    ('PROVIDER_CONSOLE', 'header.user', NULL, 'Account', 'Opens the user menu (§3.3)', NULL, NULL, 'GROUP', 'EXPAND', 'HEADER', NULL, 'Free', NULL, 'P1', NULL, true, true, 370),
    ('PROVIDER_CONSOLE', 'quick.patient', 'header.quick', 'New patient', NULL, NULL, '/patients/new', 'LINK', 'NAVIGATE', 'QUICK', 'patient.register', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P1', NULL, true, true, 10),
    ('PROVIDER_CONSOLE', 'quick.appointment', 'header.quick', 'New appointment', NULL, NULL, '/schedule/new', 'LINK', 'NAVIGATE', 'QUICK', 'appointment.book', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, true, 20),
    ('PROVIDER_CONSOLE', 'quick.walkin', 'header.quick', 'Walk-in token', NULL, NULL, '/schedule/queue/new', 'LINK', 'NAVIGATE', 'QUICK', 'appointment.book', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, true, 30),
    ('PROVIDER_CONSOLE', 'quick.invoice', 'header.quick', 'New invoice', NULL, NULL, '/billing/invoices/new', 'LINK', 'NAVIGATE', 'QUICK', 'invoice.create', 'Free', NULL, 'P2', NULL, false, true, 40),
    ('PROVIDER_CONSOLE', 'quick.lab_order', 'header.quick', 'New lab order', NULL, NULL, '/lab/orders/new', 'LINK', 'NAVIGATE', 'QUICK', 'lab.order', 'Free', ARRAY['LAB']::text[], 'P3', NULL, false, true, 50),
    ('PROVIDER_CONSOLE', 'quick.referral', 'header.quick', 'New referral', NULL, NULL, '/referrals/new', 'LINK', 'NAVIGATE', 'QUICK', 'referral.manage', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P3', NULL, false, true, 60),
    ('PROVIDER_CONSOLE', 'quick.invite', 'header.quick', 'Invite staff', NULL, NULL, '/settings/staff/invite', 'LINK', 'NAVIGATE', 'QUICK', 'user.invite', 'Free', NULL, 'P1', NULL, true, true, 70),
    ('PROVIDER_CONSOLE', 'user.profile', 'header.user', 'My profile', NULL, NULL, '/me/profile', 'LINK', 'NAVIGATE', 'USER', NULL, 'Free', NULL, 'P1', NULL, true, true, 10),
    ('PROVIDER_CONSOLE', 'user.practice', 'header.user', 'Professional profile', NULL, NULL, '/me/practice', 'LINK', 'NAVIGATE', 'USER', 'encounter.chart', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P1', NULL, true, true, 20),
    ('PROVIDER_CONSOLE', 'user.affiliations', 'header.user', 'Clinic affiliations', NULL, NULL, '/me/affiliations', 'LINK', 'NAVIGATE', 'USER', 'encounter.chart', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P1', NULL, true, true, 30),
    ('PROVIDER_CONSOLE', 'user.credentials', 'header.user', 'Licences & credentials', NULL, NULL, '/me/credentials', 'LINK', 'NAVIGATE', 'USER', 'encounter.chart', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P1', NULL, true, true, 40),
    ('PROVIDER_CONSOLE', 'user.templates', 'header.user', 'My templates & quick text', NULL, NULL, '/me/templates', 'LINK', 'NAVIGATE', 'USER', 'encounter.chart', 'Starter', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, true, 50),
    ('PROVIDER_CONSOLE', 'user.availability', 'header.user', 'My availability', NULL, NULL, '/me/availability', 'LINK', 'NAVIGATE', 'USER', 'appointment.view', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, true, 60),
    ('PROVIDER_CONSOLE', 'user.preferences', 'header.user', 'Preferences', NULL, NULL, '/me/preferences', 'LINK', 'NAVIGATE', 'USER', NULL, 'Free', NULL, 'P1', NULL, true, true, 70),
    ('PROVIDER_CONSOLE', 'user.security', 'header.user', 'Security', NULL, NULL, '/me/security', 'LINK', 'NAVIGATE', 'USER', NULL, 'Free', NULL, 'P1', NULL, true, true, 80),
    ('PROVIDER_CONSOLE', 'user.switch_org', 'header.user', 'Switch organisation', NULL, NULL, '/select-organization', 'LINK', 'NAVIGATE', 'USER', NULL, 'Free', NULL, 'P1', NULL, true, true, 90),
    ('PROVIDER_CONSOLE', 'user.sign_out', 'header.user', 'Sign out', NULL, NULL, '/logout', 'LINK', 'NAVIGATE', 'USER', NULL, 'Free', NULL, 'P1', NULL, true, true, 100),
    ('PROVIDER_CONSOLE', 'patients.new', 'patients', 'Register patient', NULL, NULL, '/patients/new', 'LINK', 'NAVIGATE', 'INNER', 'patient.register', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P1', NULL, true, false, 20),
    ('PROVIDER_CONSOLE', 'patients.record', 'patients', 'Patient record', NULL, NULL, '/patients/:id', 'LINK', 'NAVIGATE', 'INNER', 'patient.view', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P1', NULL, true, false, 30),
    ('PROVIDER_CONSOLE', 'patients.record.overview', 'patients.record', 'Overview', NULL, NULL, '/patients/:id', 'LINK', 'NAVIGATE', 'TAB', 'patient.view', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P1', NULL, true, false, 10),
    ('PROVIDER_CONSOLE', 'patients.record.timeline', 'patients.record', 'Timeline', NULL, NULL, '/patients/:id/timeline', 'LINK', 'NAVIGATE', 'TAB', 'patient.view', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 20),
    ('PROVIDER_CONSOLE', 'patients.record.encounters', 'patients.record', 'Encounters', NULL, NULL, '/patients/:id/encounters', 'LINK', 'NAVIGATE', 'TAB', 'encounter.view', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 30),
    ('PROVIDER_CONSOLE', 'patients.record.problems', 'patients.record', 'Problems & allergies', NULL, NULL, '/patients/:id/problems', 'LINK', 'NAVIGATE', 'TAB', 'encounter.view', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 40),
    ('PROVIDER_CONSOLE', 'patients.record.medications', 'patients.record', 'Medications', NULL, NULL, '/patients/:id/medications', 'LINK', 'NAVIGATE', 'TAB', 'encounter.view', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 50),
    ('PROVIDER_CONSOLE', 'patients.record.orders', 'patients.record', 'Orders & results', NULL, NULL, '/patients/:id/orders', 'LINK', 'NAVIGATE', 'TAB', 'lab.result.view', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 60),
    ('PROVIDER_CONSOLE', 'patients.record.vitals', 'patients.record', 'Vitals & growth', NULL, NULL, '/patients/:id/vitals', 'LINK', 'NAVIGATE', 'TAB', 'encounter.view', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 70),
    ('PROVIDER_CONSOLE', 'patients.record.documents', 'patients.record', 'Documents & imaging', NULL, NULL, '/patients/:id/documents', 'LINK', 'NAVIGATE', 'TAB', 'patient.view', 'Starter', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 80),
    ('PROVIDER_CONSOLE', 'patients.record.referrals', 'patients.record', 'Referrals', NULL, NULL, '/patients/:id/referrals', 'LINK', 'NAVIGATE', 'TAB', 'referral.view', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P3', NULL, false, false, 90),
    ('PROVIDER_CONSOLE', 'patients.record.billing', 'patients.record', 'Billing', NULL, NULL, '/patients/:id/billing', 'LINK', 'NAVIGATE', 'TAB', 'invoice.view', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 100),
    ('PROVIDER_CONSOLE', 'patients.record.insurance', 'patients.record', 'Insurance', NULL, NULL, '/patients/:id/insurance', 'LINK', 'NAVIGATE', 'TAB', 'patient.view', 'Starter', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 110),
    ('PROVIDER_CONSOLE', 'patients.record.consent', 'patients.record', 'Consent', NULL, NULL, '/patients/:id/consent', 'LINK', 'NAVIGATE', 'TAB', 'consent.manage', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P1', NULL, true, false, 120),
    ('PROVIDER_CONSOLE', 'patients.record.family', 'patients.record', 'Household', NULL, NULL, '/patients/:id/family', 'LINK', 'NAVIGATE', 'TAB', 'patient.view', 'Starter', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 130),
    ('PROVIDER_CONSOLE', 'patients.record.edit', 'patients.record', 'Edit demographics', NULL, NULL, '/patients/:id/edit', 'LINK', 'NAVIGATE', 'INNER', 'patient.register', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P1', NULL, true, false, 140),
    ('PROVIDER_CONSOLE', 'patients.break_glass', 'patients', 'Emergency access', NULL, NULL, '/patients/:id/break-glass', 'LINK', 'NAVIGATE', 'INNER', 'breakglass.access', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P1', NULL, true, false, 40),
    ('PROVIDER_CONSOLE', 'patients.merge', 'patients', 'Merge patients', NULL, NULL, '/patients/duplicates/:matchId', 'LINK', 'NAVIGATE', 'INNER', 'patient.merge', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P1', NULL, true, false, 50),
    ('PROVIDER_CONSOLE', 'patients.export', 'patients', 'Export record', NULL, NULL, '/patients/:id/export', 'LINK', 'NAVIGATE', 'INNER', 'patient.export', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P1', NULL, true, false, 60),
    ('PROVIDER_CONSOLE', 'encounters.chart', 'encounters', 'Chart', NULL, NULL, '/encounters/:id', 'LINK', 'NAVIGATE', 'INNER', 'encounter.chart', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 10),
    ('PROVIDER_CONSOLE', 'encounters.chart.note', 'encounters.chart', 'Note (SOAP)', NULL, NULL, '/encounters/:id', 'LINK', 'NAVIGATE', 'TAB', 'encounter.chart', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 10),
    ('PROVIDER_CONSOLE', 'encounters.chart.vitals', 'encounters.chart', 'Vitals', NULL, NULL, '/encounters/:id/vitals', 'LINK', 'NAVIGATE', 'TAB', 'vitals.record', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 20),
    ('PROVIDER_CONSOLE', 'encounters.chart.orders', 'encounters.chart', 'Orders', NULL, NULL, '/encounters/:id/orders', 'LINK', 'NAVIGATE', 'TAB', 'lab.order', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 30),
    ('PROVIDER_CONSOLE', 'encounters.chart.prescriptions', 'encounters.chart', 'Prescriptions', NULL, NULL, '/encounters/:id/prescriptions', 'LINK', 'NAVIGATE', 'TAB', 'prescription.create', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 40),
    ('PROVIDER_CONSOLE', 'encounters.chart.codes', 'encounters.chart', 'Diagnoses & codes', NULL, NULL, '/encounters/:id/codes', 'LINK', 'NAVIGATE', 'TAB', 'coding.suggest', 'Starter', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 50),
    ('PROVIDER_CONSOLE', 'encounters.chart.attachments', 'encounters.chart', 'Attachments', NULL, NULL, '/encounters/:id/attachments', 'LINK', 'NAVIGATE', 'TAB', 'encounter.chart', 'Starter', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 60),
    ('PROVIDER_CONSOLE', 'encounters.chart.scribe', 'encounters.chart', 'Ambient scribe', NULL, NULL, '/encounters/:id/scribe', 'LINK', 'NAVIGATE', 'TAB', 'ai.use', 'Paid', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P4', NULL, false, false, 70),
    ('PROVIDER_CONSOLE', 'encounters.sign', 'encounters', 'Review & sign', NULL, NULL, '/encounters/:id/sign', 'LINK', 'NAVIGATE', 'INNER', 'encounter.sign', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 20),
    ('PROVIDER_CONSOLE', 'encounters.addendum', 'encounters', 'Addendum', NULL, NULL, '/encounters/:id/addendum', 'LINK', 'NAVIGATE', 'INNER', 'encounter.sign', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 30),
    ('PROVIDER_CONSOLE', 'encounters.history', 'encounters', 'Version history', NULL, NULL, '/encounters/:id/history', 'LINK', 'NAVIGATE', 'INNER', 'encounter.view', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 40),
    ('PROVIDER_CONSOLE', 'encounters.prescription_print', 'encounters', 'Print prescription', NULL, NULL, '/encounters/:id/prescriptions/:rxId/print', 'LINK', 'NAVIGATE', 'INNER', 'prescription.sign', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 50),
    ('PROVIDER_CONSOLE', 'encounters.offline_conflicts', 'encounters', 'Sync conflicts', NULL, NULL, '/encounters/conflicts', 'LINK', 'NAVIGATE', 'INNER', 'encounter.chart', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 60),
    ('PROVIDER_CONSOLE', 'inbox.results', 'inbox', 'Results to review', NULL, NULL, '/inbox/results', 'LINK', 'NAVIGATE', 'TAB', 'lab.result.view', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 10),
    ('PROVIDER_CONSOLE', 'inbox.critical', 'inbox', 'Critical values', NULL, NULL, '/inbox/critical', 'LINK', 'NAVIGATE', 'TAB', 'lab.result.view', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 20),
    ('PROVIDER_CONSOLE', 'inbox.unsigned', 'inbox', 'Unsigned notes', NULL, NULL, '/inbox/unsigned', 'LINK', 'NAVIGATE', 'TAB', 'encounter.sign', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 30),
    ('PROVIDER_CONSOLE', 'inbox.refills', 'inbox', 'Refill requests', NULL, NULL, '/inbox/refills', 'LINK', 'NAVIGATE', 'TAB', 'prescription.create', 'Starter', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 40),
    ('PROVIDER_CONSOLE', 'inbox.referrals', 'inbox', 'Referral updates', NULL, NULL, '/inbox/referrals', 'LINK', 'NAVIGATE', 'TAB', 'referral.view', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P3', NULL, false, false, 50),
    ('PROVIDER_CONSOLE', 'inbox.queries', 'inbox', 'Coder queries', NULL, NULL, '/inbox/queries', 'LINK', 'NAVIGATE', 'TAB', 'encounter.sign', 'Pro', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P4', NULL, false, false, 60),
    ('PROVIDER_CONSOLE', 'inbox.messages', 'inbox', 'Patient messages', NULL, NULL, '/inbox/messages', 'LINK', 'NAVIGATE', 'TAB', 'encounter.view', 'Pro', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P3', NULL, false, false, 70),
    ('PROVIDER_CONSOLE', 'schedule.new', 'schedule', 'Book appointment', NULL, NULL, '/schedule/new', 'LINK', 'NAVIGATE', 'INNER', 'appointment.book', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 30),
    ('PROVIDER_CONSOLE', 'schedule.appointment', 'schedule', 'Appointment', NULL, NULL, '/schedule/appointments/:id', 'LINK', 'NAVIGATE', 'INNER', 'appointment.view', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 40),
    ('PROVIDER_CONSOLE', 'schedule.series', 'schedule', 'Recurring series', NULL, NULL, '/schedule/series/:id', 'LINK', 'NAVIGATE', 'INNER', 'appointment.book', 'Starter', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 50),
    ('PROVIDER_CONSOLE', 'orders.detail', 'orders', 'Order', NULL, NULL, '/orders/:id', 'LINK', 'NAVIGATE', 'INNER', 'lab.order', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 10),
    ('PROVIDER_CONSOLE', 'orders.result', 'orders', 'Result', NULL, NULL, '/orders/:id/result', 'LINK', 'NAVIGATE', 'INNER', 'lab.result.view', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 20),
    ('PROVIDER_CONSOLE', 'orders.trend', 'orders', 'Analyte trend', NULL, NULL, '/orders/trends/:loinc', 'LINK', 'NAVIGATE', 'INNER', 'lab.result.view', 'Starter', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, false, 30),
    ('PROVIDER_CONSOLE', 'telehealth.consult', 'telehealth', 'Consult room', NULL, NULL, '/telehealth/:consultId', 'LINK', 'NAVIGATE', 'INNER', 'telehealth.consult', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P4', NULL, false, false, 10),
    ('PROVIDER_CONSOLE', 'referrals.new', 'referrals', 'New referral', NULL, NULL, '/referrals/new', 'LINK', 'NAVIGATE', 'INNER', 'referral.manage', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P3', NULL, false, false, 10),
    ('PROVIDER_CONSOLE', 'referrals.detail', 'referrals', 'Referral', NULL, NULL, '/referrals/:id', 'LINK', 'NAVIGATE', 'INNER', 'referral.view', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P3', NULL, false, false, 20),
    ('PROVIDER_CONSOLE', 'lab.orders.new', 'lab.orders', 'New order', NULL, NULL, '/lab/orders/new', 'LINK', 'NAVIGATE', 'INNER', 'lab.order', 'Free', ARRAY['LAB']::text[], 'P3', NULL, false, false, 10),
    ('PROVIDER_CONSOLE', 'lab.orders.detail', 'lab.orders', 'Lab order', NULL, NULL, '/lab/orders/:id', 'LINK', 'NAVIGATE', 'INNER', 'lab.order', 'Free', ARRAY['LAB']::text[], 'P3', NULL, false, false, 20),
    ('PROVIDER_CONSOLE', 'lab.results.upload', 'lab.results', 'Bulk result upload', NULL, NULL, '/lab/results/upload', 'LINK', 'NAVIGATE', 'INNER', 'lab.result.upload', 'Free', ARRAY['LAB']::text[], 'P3', NULL, false, false, 10),
    ('PROVIDER_CONSOLE', 'pharmacy.dispense', NULL, 'Dispense', NULL, NULL, '/pharmacy/queue/:rxId', 'LINK', 'NAVIGATE', 'INNER', 'prescription.dispense', 'Free', ARRAY['STORE']::text[], 'P3', NULL, false, false, 380),
    ('PROVIDER_CONSOLE', 'pharmacy.inventory.stock', 'pharmacy.inventory', 'Stock', NULL, NULL, '/pharmacy/inventory', 'LINK', 'NAVIGATE', 'TAB', 'pharmacy.inventory.manage', 'Starter', ARRAY['STORE']::text[], 'P3', NULL, false, false, 10),
    ('PROVIDER_CONSOLE', 'pharmacy.inventory.expiring', 'pharmacy.inventory', 'Expiring', NULL, NULL, '/pharmacy/inventory/expiring', 'LINK', 'NAVIGATE', 'TAB', 'pharmacy.inventory.manage', 'Starter', ARRAY['STORE']::text[], 'P3', NULL, false, false, 20),
    ('PROVIDER_CONSOLE', 'pharmacy.inventory.reorder', 'pharmacy.inventory', 'Reorder', NULL, NULL, '/pharmacy/inventory/reorder', 'LINK', 'NAVIGATE', 'TAB', 'pharmacy.inventory.manage', 'Starter', ARRAY['STORE']::text[], 'P3', NULL, false, false, 30),
    ('PROVIDER_CONSOLE', 'pharmacy.inventory.receive', 'pharmacy.inventory', 'Receive stock', NULL, NULL, '/pharmacy/inventory/receive', 'LINK', 'NAVIGATE', 'INNER', 'pharmacy.inventory.manage', 'Starter', ARRAY['STORE']::text[], 'P3', NULL, false, false, 40),
    ('PROVIDER_CONSOLE', 'billing.invoices.new', 'billing.invoices', 'New invoice', NULL, NULL, '/billing/invoices/new', 'LINK', 'NAVIGATE', 'INNER', 'invoice.create', 'Free', NULL, 'P2', NULL, false, false, 10),
    ('PROVIDER_CONSOLE', 'billing.invoices.detail', 'billing.invoices', 'Invoice', NULL, NULL, '/billing/invoices/:id', 'LINK', 'NAVIGATE', 'INNER', 'invoice.view', 'Free', NULL, 'P2', NULL, false, false, 20),
    ('PROVIDER_CONSOLE', 'claims.detail', 'claims', 'Claim', NULL, NULL, '/claims/:id', 'LINK', 'NAVIGATE', 'INNER', 'claim.manage', 'Pro', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P4', NULL, false, false, 40),
    ('PROVIDER_CONSOLE', 'coding.encounter', 'coding', 'Code encounter', NULL, NULL, '/coding/:encounterId', 'LINK', 'NAVIGATE', 'INNER', 'coding.assign', 'Pro', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P4', NULL, false, false, 10),
    ('PROVIDER_CONSOLE', 'coding.audit', 'coding', 'Accuracy audit', NULL, NULL, '/coding/audit', 'LINK', 'NAVIGATE', 'TAB', 'coding.assign', 'Pro', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P4', NULL, false, false, 20),
    ('PROVIDER_CONSOLE', 'ai_review.scribe', 'ai_review', 'Scribe drafts', NULL, NULL, '/ai-review/scribe', 'LINK', 'NAVIGATE', 'TAB', 'ai.use', 'Paid', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P4', NULL, false, false, 10),
    ('PROVIDER_CONSOLE', 'ai_review.coding', 'ai_review', 'Code suggestions', NULL, NULL, '/ai-review/coding', 'LINK', 'NAVIGATE', 'TAB', 'ai.use', 'Paid', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P4', NULL, false, false, 20),
    ('PROVIDER_CONSOLE', 'ai_review.triage', 'ai_review', 'Result triage', NULL, NULL, '/ai-review/triage', 'LINK', 'NAVIGATE', 'TAB', 'ai.use', 'Paid', NULL, 'P4', NULL, false, false, 30),
    ('PROVIDER_CONSOLE', 'ai_review.claims', 'ai_review', 'Claim packages', NULL, NULL, '/ai-review/claims', 'LINK', 'NAVIGATE', 'TAB', 'ai.use', 'Paid', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P4', NULL, false, false, 40),
    ('PROVIDER_CONSOLE', 'ai_review.trace', 'ai_review', 'Agent trace', NULL, NULL, '/ai-review/traces/:id', 'LINK', 'NAVIGATE', 'INNER', 'ai.use', 'Paid', NULL, 'P4', NULL, false, false, 50),
    ('PROVIDER_CONSOLE', 'reports.operations', 'reports', 'Operations', NULL, NULL, '/reports/operations', 'LINK', 'NAVIGATE', 'TAB', 'report.view', 'Starter', NULL, 'P2', NULL, false, false, 10),
    ('PROVIDER_CONSOLE', 'reports.revenue', 'reports', 'Revenue', NULL, NULL, '/reports/revenue', 'LINK', 'NAVIGATE', 'TAB', 'billing.view', 'Starter', NULL, 'P2', NULL, false, false, 20),
    ('PROVIDER_CONSOLE', 'reports.clinical', 'reports', 'Care gaps & quality', NULL, NULL, '/reports/clinical', 'LINK', 'NAVIGATE', 'TAB', 'report.view', 'Pro', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P3', NULL, false, false, 30),
    ('PROVIDER_CONSOLE', 'reports.referrals', 'reports', 'Referral analytics', NULL, NULL, '/reports/referrals', 'LINK', 'NAVIGATE', 'TAB', 'report.view', 'Pro', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P3', NULL, false, false, 40),
    ('PROVIDER_CONSOLE', 'reports.lab', 'reports', 'Lab performance', NULL, NULL, '/reports/lab', 'LINK', 'NAVIGATE', 'TAB', 'report.view', 'Pro', ARRAY['LAB']::text[], 'P3', NULL, false, false, 50),
    ('PROVIDER_CONSOLE', 'reports.pharmacy', 'reports', 'Pharmacy', NULL, NULL, '/reports/pharmacy', 'LINK', 'NAVIGATE', 'TAB', 'report.view', 'Pro', ARRAY['STORE']::text[], 'P3', NULL, false, false, 60),
    ('PROVIDER_CONSOLE', 'reports.ai', 'reports', 'Agent performance', NULL, NULL, '/reports/ai', 'LINK', 'NAVIGATE', 'TAB', 'ai.manage', 'Pro', NULL, 'P4', NULL, false, false, 70),
    ('PROVIDER_CONSOLE', 'notifications.all', NULL, 'All notifications', NULL, NULL, '/notifications', 'LINK', 'NAVIGATE', 'INNER', NULL, 'Free', NULL, 'P1', NULL, true, false, 390),
    ('PROVIDER_CONSOLE', 'settings.group.organisation', 'settings', 'Organisation', NULL, NULL, NULL, 'GROUP', 'EXPAND', 'SETTINGS', NULL, 'Free', NULL, 'P1', NULL, true, true, 10),
    ('PROVIDER_CONSOLE', 'settings.organisation', 'settings.group.organisation', 'Organisation profile', 'Name, contact, logo, address, registration numbers', NULL, '/settings/organisation', 'LINK', 'NAVIGATE', 'SETTINGS', 'settings.manage', 'Free', NULL, 'P1', NULL, true, true, 10),
    ('PROVIDER_CONSOLE', 'settings.verification', 'settings.group.organisation', 'Verification', 'Legal profile, licences/accreditation (NABL, drug licence), submit for review, status', NULL, '/settings/verification', 'LINK', 'NAVIGATE', 'SETTINGS', 'settings.manage', 'Free', NULL, 'P1', NULL, true, true, 20),
    ('PROVIDER_CONSOLE', 'settings.locations', 'settings.group.organisation', 'Locations', 'Sites, hours, rooms per site', NULL, '/settings/locations', 'LINK', 'NAVIGATE', 'SETTINGS', 'settings.manage', 'Starter', NULL, 'P2', NULL, false, true, 30),
    ('PROVIDER_CONSOLE', 'settings.branding', 'settings.group.organisation', 'Branding', 'Colours, logo on prints and portal', NULL, '/settings/branding', 'LINK', 'NAVIGATE', 'SETTINGS', 'branding.manage', 'Starter', NULL, 'P1', NULL, true, true, 40),
    ('PROVIDER_CONSOLE', 'settings.subscription', 'settings.group.organisation', 'Plan & billing', 'CliniqAI plan, usage, invoices, upgrade', NULL, '/settings/subscription', 'LINK', 'NAVIGATE', 'SETTINGS', 'billing.view', 'Free', NULL, 'P1', NULL, true, true, 50),
    ('PROVIDER_CONSOLE', 'settings.group.users_access', 'settings', 'Users & access', NULL, NULL, NULL, 'GROUP', 'EXPAND', 'SETTINGS', NULL, 'Free', NULL, 'P1', NULL, true, true, 20),
    ('PROVIDER_CONSOLE', 'settings.staff', 'settings.group.users_access', 'Staff', 'Invite, assign role/location, deactivate (revokes access at once)', NULL, '/settings/staff', 'LINK', 'NAVIGATE', 'SETTINGS', 'user.manage', 'Free', NULL, 'P1', NULL, true, true, 10),
    ('PROVIDER_CONSOLE', 'settings.staff.detail', 'settings.staff', 'Staff member', 'Roles, locations, credentials, activity', NULL, '/settings/staff/:id', 'LINK', 'NAVIGATE', 'INNER', 'user.manage', 'Free', NULL, 'P1', NULL, true, false, 10),
    ('PROVIDER_CONSOLE', 'settings.staff.invite', 'settings.staff', 'Invite staff', NULL, NULL, '/settings/staff/invite', 'LINK', 'NAVIGATE', 'INNER', 'user.invite', 'Free', NULL, 'P1', NULL, true, false, 20),
    ('PROVIDER_CONSOLE', 'settings.roles', 'settings.group.users_access', 'Roles & permissions', 'Built-in roles; custom role builder', NULL, '/settings/roles', 'LINK', 'NAVIGATE', 'SETTINGS', 'role.manage', 'Starter', NULL, 'P1', NULL, true, true, 20),
    ('PROVIDER_CONSOLE', 'settings.groups', 'settings.group.users_access', 'User groups', 'Groups for menu targeting (Stage 3)', NULL, '/settings/groups', 'LINK', 'NAVIGATE', 'SETTINGS', 'group.manage', 'Free', NULL, 'P1', NULL, true, true, 30),
    ('PROVIDER_CONSOLE', 'settings.sso', 'settings.group.users_access', 'Single sign-on', 'OIDC identity provider for staff', NULL, '/settings/sso', 'LINK', 'NAVIGATE', 'SETTINGS', 'settings.manage', 'Pro', NULL, 'P3', NULL, false, true, 40),
    ('PROVIDER_CONSOLE', 'settings.security', 'settings.group.users_access', 'Security policy', 'MFA rules, session timeout (above the floor)', NULL, '/settings/security', 'LINK', 'NAVIGATE', 'SETTINGS', 'settings.manage', 'Free', NULL, 'P1', NULL, true, true, 50),
    ('PROVIDER_CONSOLE', 'settings.group.navigation', 'settings', 'Navigation', NULL, NULL, NULL, 'GROUP', 'EXPAND', 'SETTINGS', NULL, 'Free', NULL, 'P1', NULL, true, true, 30),
    ('PROVIDER_CONSOLE', 'settings.navigation', 'settings.group.navigation', 'Menu', 'Draft editor, preview as user/role, publish, versions, rollback (Stage 2)', NULL, '/settings/navigation', 'LINK', 'NAVIGATE', 'SETTINGS', 'menu.manage', 'Free', NULL, 'P1', NULL, true, true, 10),
    ('PROVIDER_CONSOLE', 'settings.navigation.temporary', 'settings.navigation', 'Announcements', 'Time-bound menus and campaigns (Stage 4)', NULL, '/settings/navigation/temporary', 'LINK', 'NAVIGATE', 'SETTINGS', 'menu.manage', 'Free', NULL, 'P1', NULL, true, true, 10),
    ('PROVIDER_CONSOLE', 'settings.group.scheduling', 'settings', 'Scheduling', NULL, NULL, NULL, 'GROUP', 'EXPAND', 'SETTINGS', NULL, 'Free', NULL, 'P2', NULL, false, true, 40),
    ('PROVIDER_CONSOLE', 'settings.scheduling.types', 'settings.group.scheduling', 'Appointment types', 'Durations, resources, prep instructions', NULL, '/settings/scheduling/appointment-types', 'LINK', 'NAVIGATE', 'SETTINGS', 'schedule.manage', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, true, 10),
    ('PROVIDER_CONSOLE', 'settings.scheduling.availability', 'settings.group.scheduling', 'Provider availability', 'Templates, exceptions, leave, per-location hours', NULL, '/settings/scheduling/availability', 'LINK', 'NAVIGATE', 'SETTINGS', 'schedule.manage', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, true, 20),
    ('PROVIDER_CONSOLE', 'settings.scheduling.resources', 'settings.group.scheduling', 'Rooms & equipment', 'Bookable resources', NULL, '/settings/scheduling/resources', 'LINK', 'NAVIGATE', 'SETTINGS', 'schedule.manage', 'Pro', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, true, 30),
    ('PROVIDER_CONSOLE', 'settings.scheduling.policies', 'settings.group.scheduling', 'Booking policies', 'Reminders cadence, cancellation fee window, waitlist rules', NULL, '/settings/scheduling/policies', 'LINK', 'NAVIGATE', 'SETTINGS', 'schedule.manage', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, true, 40),
    ('PROVIDER_CONSOLE', 'settings.group.clinical', 'settings', 'Clinical', NULL, NULL, NULL, 'GROUP', 'EXPAND', 'SETTINGS', NULL, 'Free', NULL, 'P2', NULL, false, true, 50),
    ('PROVIDER_CONSOLE', 'settings.clinical.templates', 'settings.group.clinical', 'Note templates', 'Specialty SOAP templates', NULL, '/settings/clinical/templates', 'LINK', 'NAVIGATE', 'SETTINGS', 'settings.manage', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, true, 10),
    ('PROVIDER_CONSOLE', 'settings.clinical.order_sets', 'settings.group.clinical', 'Order sets', 'Panels and clinic favourites', NULL, '/settings/clinical/order-sets', 'LINK', 'NAVIGATE', 'SETTINGS', 'settings.manage', 'Starter', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, true, 20),
    ('PROVIDER_CONSOLE', 'settings.clinical.formulary', 'settings.group.clinical', 'Formulary', 'Clinic formulary and favourites', NULL, '/settings/clinical/formulary', 'LINK', 'NAVIGATE', 'SETTINGS', 'settings.manage', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, true, 30),
    ('PROVIDER_CONSOLE', 'settings.clinical.intake_forms', 'settings.group.clinical', 'Intake forms', 'Pre-visit questionnaires', NULL, '/settings/clinical/intake-forms', 'LINK', 'NAVIGATE', 'SETTINGS', 'settings.manage', 'Starter', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, true, 40),
    ('PROVIDER_CONSOLE', 'settings.clinical.result_release', 'settings.group.clinical', 'Result release', 'Embargo per result type before patients see it', NULL, '/settings/clinical/result-release', 'LINK', 'NAVIGATE', 'SETTINGS', 'settings.manage', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P2', NULL, false, true, 50),
    ('PROVIDER_CONSOLE', 'settings.clinical.cds', 'settings.group.clinical', 'Decision support', 'Guideline prompts and care-gap rules', NULL, '/settings/clinical/cds', 'LINK', 'NAVIGATE', 'SETTINGS', 'settings.manage', 'Pro', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P3', NULL, false, true, 60),
    ('PROVIDER_CONSOLE', 'settings.telehealth', 'settings.group.clinical', 'Telehealth', 'Consult modes, recording retention, waiting-room text', NULL, '/settings/telehealth', 'LINK', 'NAVIGATE', 'SETTINGS', 'settings.manage', 'Free', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P4', NULL, false, true, 70),
    ('PROVIDER_CONSOLE', 'settings.group.lab', 'settings', 'Lab', NULL, NULL, NULL, 'GROUP', 'EXPAND', 'SETTINGS', NULL, 'Free', NULL, 'P3', NULL, false, true, 60),
    ('PROVIDER_CONSOLE', 'settings.lab.catalog', 'settings.group.lab', 'Test catalog', 'Tests, panels, sample types', NULL, '/settings/lab/catalog', 'LINK', 'NAVIGATE', 'SETTINGS', 'lab.catalog.manage', 'Free', ARRAY['LAB']::text[], 'P3', NULL, false, true, 10),
    ('PROVIDER_CONSOLE', 'settings.lab.pricing', 'settings.group.lab', 'Price list', 'Prices per test and channel', NULL, '/settings/lab/pricing', 'LINK', 'NAVIGATE', 'SETTINGS', 'lab.catalog.manage', 'Free', ARRAY['LAB']::text[], 'P3', NULL, false, true, 20),
    ('PROVIDER_CONSOLE', 'settings.lab.loinc', 'settings.group.lab', 'LOINC mapping', 'Local code → LOINC', NULL, '/settings/lab/loinc-mapping', 'LINK', 'NAVIGATE', 'SETTINGS', 'lab.catalog.manage', 'Free', ARRAY['LAB']::text[], 'P3', NULL, false, true, 30),
    ('PROVIDER_CONSOLE', 'settings.lab.tat', 'settings.group.lab', 'Turnaround targets', 'TAT per test; SLA alerts', NULL, '/settings/lab/tat', 'LINK', 'NAVIGATE', 'SETTINGS', 'lab.catalog.manage', 'Pro', ARRAY['LAB']::text[], 'P3', NULL, false, true, 40),
    ('PROVIDER_CONSOLE', 'settings.group.pharmacy', 'settings', 'Pharmacy', NULL, NULL, NULL, 'GROUP', 'EXPAND', 'SETTINGS', NULL, 'Free', NULL, 'P3', NULL, false, true, 70),
    ('PROVIDER_CONSOLE', 'settings.pharmacy.catalog', 'settings.group.pharmacy', 'Catalog & pricing', 'Drugs stocked, MRP, discounts', NULL, '/settings/pharmacy/catalog', 'LINK', 'NAVIGATE', 'SETTINGS', 'pharmacy.inventory.manage', 'Free', ARRAY['STORE']::text[], 'P3', NULL, false, true, 10),
    ('PROVIDER_CONSOLE', 'settings.pharmacy.substitution', 'settings.group.pharmacy', 'Substitution rules', 'When a generic may replace a brand', NULL, '/settings/pharmacy/substitution-rules', 'LINK', 'NAVIGATE', 'SETTINGS', 'pharmacy.inventory.manage', 'Starter', ARRAY['STORE']::text[], 'P3', NULL, false, true, 20),
    ('PROVIDER_CONSOLE', 'settings.pharmacy.delivery', 'settings.group.pharmacy', 'Delivery', 'Areas, slots, charges', NULL, '/settings/pharmacy/delivery', 'LINK', 'NAVIGATE', 'SETTINGS', 'pharmacy.inventory.manage', 'Starter', ARRAY['STORE']::text[], 'P3', NULL, false, true, 30),
    ('PROVIDER_CONSOLE', 'settings.group.billing', 'settings', 'Billing', NULL, NULL, NULL, 'GROUP', 'EXPAND', 'SETTINGS', NULL, 'Free', NULL, 'P2', NULL, false, true, 80),
    ('PROVIDER_CONSOLE', 'settings.billing.charge_master', 'settings.group.billing', 'Charge master', 'Services and fee schedules (cash, insurance, corporate, camp)', NULL, '/settings/billing/charge-master', 'LINK', 'NAVIGATE', 'SETTINGS', 'billing.manage', 'Free', NULL, 'P2', NULL, false, true, 10),
    ('PROVIDER_CONSOLE', 'settings.billing.tax', 'settings.group.billing', 'Tax & invoices', 'GSTIN, invoice series, print layout, languages', NULL, '/settings/billing/tax', 'LINK', 'NAVIGATE', 'SETTINGS', 'billing.manage', 'Free', NULL, 'P2', NULL, false, true, 20),
    ('PROVIDER_CONSOLE', 'settings.billing.payment_methods', 'settings.group.billing', 'Payment methods', 'UPI, gateway, cash counters', NULL, '/settings/billing/payment-methods', 'LINK', 'NAVIGATE', 'SETTINGS', 'billing.manage', 'Free', NULL, 'P2', NULL, false, true, 30),
    ('PROVIDER_CONSOLE', 'settings.billing.payers', 'settings.group.billing', 'Payers', 'Insurers, TPAs, PMJAY, corporate panels', NULL, '/settings/billing/payers', 'LINK', 'NAVIGATE', 'SETTINGS', 'billing.manage', 'Starter', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P3', NULL, false, true, 40),
    ('PROVIDER_CONSOLE', 'settings.group.integrations_data', 'settings', 'Integrations & data', NULL, NULL, NULL, 'GROUP', 'EXPAND', 'SETTINGS', NULL, 'Free', NULL, 'P1', NULL, true, true, 90),
    ('PROVIDER_CONSOLE', 'settings.integrations.abdm', 'settings.group.integrations_data', 'ABDM', 'HIP/HIU registration, facility ID, consent manager', NULL, '/settings/integrations/abdm', 'LINK', 'NAVIGATE', 'SETTINGS', 'integration.manage', 'Free', NULL, 'P1', NULL, true, true, 10),
    ('PROVIDER_CONSOLE', 'settings.integrations.connections', 'settings.group.integrations_data', 'Connected systems', 'HL7 v2 / FHIR endpoints, LIS/HIS', NULL, '/settings/integrations/connections', 'LINK', 'NAVIGATE', 'SETTINGS', 'integration.manage', 'Pro', NULL, 'P3', NULL, false, true, 20),
    ('PROVIDER_CONSOLE', 'settings.integrations.api', 'settings.group.integrations_data', 'API & webhooks', 'API clients, keys, webhook subscriptions', NULL, '/settings/integrations/api', 'LINK', 'NAVIGATE', 'SETTINGS', 'integration.manage', 'Pro', NULL, 'P3', NULL, false, true, 30),
    ('PROVIDER_CONSOLE', 'settings.data.import', 'settings.group.integrations_data', 'Import data', 'Bulk import with reviewable mapping (all plans)', NULL, '/settings/data/import', 'LINK', 'NAVIGATE', 'SETTINGS', 'data.import', 'Free', NULL, 'P1', NULL, true, true, 40),
    ('PROVIDER_CONSOLE', 'settings.data.export', 'settings.group.integrations_data', 'Export data', 'Flat FHIR export', NULL, '/settings/data/export', 'LINK', 'NAVIGATE', 'SETTINGS', 'data.export', 'Free', NULL, 'P1', NULL, true, true, 50),
    ('PROVIDER_CONSOLE', 'settings.group.ai', 'settings', 'AI', NULL, NULL, NULL, 'GROUP', 'EXPAND', 'SETTINGS', NULL, 'Free', NULL, 'P4', NULL, false, true, 100),
    ('PROVIDER_CONSOLE', 'settings.ai.agents', 'settings.group.ai', 'Agents & autonomy', 'Enable agents, lower autonomy, tenant kill switch', NULL, '/settings/ai/agents', 'LINK', 'NAVIGATE', 'SETTINGS', 'ai.manage', 'Paid', NULL, 'P4', NULL, false, true, 10),
    ('PROVIDER_CONSOLE', 'settings.ai.budget', 'settings.group.ai', 'AI budget', 'Spend caps and usage', NULL, '/settings/ai/budget', 'LINK', 'NAVIGATE', 'SETTINGS', 'ai.manage', 'Paid', NULL, 'P4', NULL, false, true, 20),
    ('PROVIDER_CONSOLE', 'settings.group.communication', 'settings', 'Communication', NULL, NULL, NULL, 'GROUP', 'EXPAND', 'SETTINGS', NULL, 'Free', NULL, 'P1', NULL, true, true, 110),
    ('PROVIDER_CONSOLE', 'settings.notifications', 'settings.group.communication', 'Notifications', 'Channels (SMS, WhatsApp, email), templates, languages', NULL, '/settings/notifications', 'LINK', 'NAVIGATE', 'SETTINGS', 'settings.manage', 'Free', NULL, 'P1', NULL, true, true, 10),
    ('PROVIDER_CONSOLE', 'settings.kiosks', 'settings.group.communication', 'Kiosks', 'Pair devices, home tiles, idle timeout', NULL, '/settings/kiosks', 'LINK', 'NAVIGATE', 'SETTINGS', 'settings.manage', 'Starter', ARRAY['CLINIC','PRIVATE_CLINIC']::text[], 'P3', NULL, false, true, 20),
    ('PROVIDER_CONSOLE', 'settings.group.compliance', 'settings', 'Compliance', NULL, NULL, NULL, 'GROUP', 'EXPAND', 'SETTINGS', NULL, 'Free', NULL, 'P1', NULL, true, true, 120),
    ('PROVIDER_CONSOLE', 'settings.compliance.retention', 'settings.group.compliance', 'Retention & legal hold', 'Retention rules, holds', NULL, '/settings/compliance/retention', 'LINK', 'NAVIGATE', 'SETTINGS', 'compliance.monitor', 'Free', NULL, 'P1', NULL, true, true, 10),
    ('PROVIDER_CONSOLE', 'settings.compliance.consent_forms', 'settings.group.compliance', 'Consent forms', 'Consent-to-treat / AI / share wording and versions', NULL, '/settings/compliance/consent-forms', 'LINK', 'NAVIGATE', 'SETTINGS', 'consent.manage', 'Free', NULL, 'P1', NULL, true, true, 20),
    ('PATIENT_PORTAL', 'pp.home', NULL, 'Home', 'Next appointment, alerts, quick actions', 'Home', '/', 'LINK', 'NAVIGATE', 'LEFT', 'dashboard.view', 'Free', NULL, 'P3', 'M15', false, true, 10),
    ('PATIENT_PORTAL', 'pp.appointments', NULL, 'Appointments', 'Upcoming and past; book, reschedule, cancel', 'Calendar', '/appointments', 'LINK', 'NAVIGATE', 'LEFT', 'appointment.view', 'Free', NULL, 'P3', 'M15', false, true, 20),
    ('PATIENT_PORTAL', 'pp.records', NULL, 'Records', 'Unified timeline across all providers', 'FileText', '/records', 'LINK', 'NAVIGATE', 'LEFT', 'lab.result.view', 'Free', NULL, 'P3', 'M15', false, true, 30),
    ('PATIENT_PORTAL', 'pp.medicines', NULL, 'Medicines', 'Prescriptions, refills, order medicines', 'Pill', '/medicines', 'LINK', 'NAVIGATE', 'LEFT', 'lab.result.view', 'Free', NULL, 'P3', 'M15', false, true, 40),
    ('PATIENT_PORTAL', 'pp.payments', NULL, 'Payments', 'Bills, pay, history', 'CreditCard', '/payments', 'LINK', 'NAVIGATE', 'LEFT', 'invoice.view', 'Free', NULL, 'P3', 'M15', false, true, 50),
    ('PATIENT_PORTAL', 'pp.find_care', NULL, 'Find care', 'Search verified doctors, clinics, labs', 'Search', '/find-care', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P3', NULL, false, true, 60),
    ('PATIENT_PORTAL', 'pp.lab_tests', NULL, 'Book a lab test', 'Direct lab booking where allowed; home collection', 'TestTube', '/lab-tests', 'LINK', 'NAVIGATE', 'LEFT', 'appointment.book', 'Starter', NULL, 'P3', NULL, false, true, 70),
    ('PATIENT_PORTAL', 'pp.consult', NULL, 'Consult online', 'Join a telehealth consult / waiting room', 'Video', '/consult', 'LINK', 'NAVIGATE', 'LEFT', 'appointment.view', 'Free', NULL, 'P4', NULL, false, true, 80),
    ('PATIENT_PORTAL', 'pp.messages', NULL, 'Messages', 'Secure messaging with care team', 'MessageSquare', '/messages', 'LINK', 'NAVIGATE', 'LEFT', 'appointment.view', 'Pro', NULL, 'P3', NULL, false, true, 90),
    ('PATIENT_PORTAL', 'pp.family', NULL, 'Family', 'Dependents, caregivers, delegated access', 'Users', '/family', 'LINK', 'NAVIGATE', 'LEFT', 'appointment.view', 'Starter', NULL, 'P3', NULL, false, true, 100),
    ('PATIENT_PORTAL', 'pp.privacy', NULL, 'Privacy & consent', 'Consent centre (§4.3)', 'ShieldCheck', '/privacy', 'LINK', 'NAVIGATE', 'LEFT', 'patient.export', 'Free', NULL, 'P3', NULL, false, true, 110),
    ('PATIENT_PORTAL', 'pp.appointments.book', 'pp.appointments', 'Book appointment', NULL, NULL, '/appointments/book', 'LINK', 'NAVIGATE', 'INNER', 'appointment.view', 'Free', NULL, 'P3', NULL, false, false, 10),
    ('PATIENT_PORTAL', 'pp.appointments.detail', 'pp.appointments', 'Appointment', NULL, NULL, '/appointments/:id', 'LINK', 'NAVIGATE', 'INNER', 'appointment.view', 'Free', NULL, 'P3', NULL, false, false, 20),
    ('PATIENT_PORTAL', 'pp.records.visits', 'pp.records', 'Visits', NULL, NULL, '/records/visits', 'LINK', 'NAVIGATE', 'TAB', 'lab.result.view', 'Free', NULL, 'P3', NULL, false, false, 10),
    ('PATIENT_PORTAL', 'pp.records.results', 'pp.records', 'Lab results', NULL, NULL, '/records/results', 'LINK', 'NAVIGATE', 'TAB', 'lab.result.view', 'Free', NULL, 'P3', NULL, false, false, 20),
    ('PATIENT_PORTAL', 'pp.records.documents', 'pp.records', 'Documents', NULL, NULL, '/records/documents', 'LINK', 'NAVIGATE', 'TAB', 'lab.result.view', 'Free', NULL, 'P3', NULL, false, false, 30),
    ('PATIENT_PORTAL', 'pp.records.vitals', 'pp.records', 'Vitals', NULL, NULL, '/records/vitals', 'LINK', 'NAVIGATE', 'TAB', 'lab.result.view', 'Starter', NULL, 'P3', NULL, false, false, 40),
    ('PATIENT_PORTAL', 'pp.records.detail', 'pp.records', 'Record', NULL, NULL, '/records/:id', 'LINK', 'NAVIGATE', 'INNER', 'lab.result.view', 'Free', NULL, 'P3', NULL, false, false, 50),
    ('PATIENT_PORTAL', 'pp.records.share', 'pp.records', 'Share records', NULL, NULL, '/records/share', 'LINK', 'NAVIGATE', 'INNER', 'lab.result.view', 'Free', NULL, 'P3', NULL, false, false, 60),
    ('PATIENT_PORTAL', 'pp.medicines.order', 'pp.medicines', 'Order medicines', NULL, NULL, '/medicines/order', 'LINK', 'NAVIGATE', 'INNER', 'lab.result.view', 'Starter', NULL, 'P3', NULL, false, false, 10),
    ('PATIENT_PORTAL', 'pp.medicines.refill', 'pp.medicines', 'Request refill', NULL, NULL, '/medicines/:rxId/refill', 'LINK', 'NAVIGATE', 'INNER', 'lab.result.view', 'Starter', NULL, 'P3', NULL, false, false, 20),
    ('PATIENT_PORTAL', 'pp.payments.pay', 'pp.payments', 'Pay bill', NULL, NULL, '/payments/:invoiceId', 'LINK', 'NAVIGATE', 'INNER', 'invoice.view', 'Free', NULL, 'P3', NULL, false, false, 10),
    ('PATIENT_PORTAL', 'pp.privacy.access', 'pp.privacy', 'Who can see my data', NULL, NULL, '/privacy', 'LINK', 'NAVIGATE', 'TAB', 'patient.export', 'Free', NULL, 'P3', NULL, false, false, 10),
    ('PATIENT_PORTAL', 'pp.privacy.history', 'pp.privacy', 'Access history', NULL, NULL, '/privacy/history', 'LINK', 'NAVIGATE', 'TAB', 'patient.export', 'Free', NULL, 'P3', NULL, false, false, 20),
    ('PATIENT_PORTAL', 'pp.privacy.share_links', 'pp.privacy', 'Share links', NULL, NULL, '/privacy/share-links', 'LINK', 'NAVIGATE', 'TAB', 'patient.export', 'Free', NULL, 'P3', NULL, false, false, 30),
    ('PATIENT_PORTAL', 'pp.privacy.requests', 'pp.privacy', 'Corrections & erasure', NULL, NULL, '/privacy/requests', 'LINK', 'NAVIGATE', 'TAB', 'patient.export', 'Free', NULL, 'P3', NULL, false, false, 40),
    ('PATIENT_PORTAL', 'pp.privacy.export', 'pp.privacy', 'Download my data', NULL, NULL, '/privacy/export', 'LINK', 'NAVIGATE', 'INNER', 'patient.export', 'Free', NULL, 'P3', NULL, false, false, 50),
    ('PATIENT_PORTAL', 'pp.consult.room', 'pp.consult', 'Consult room', NULL, NULL, '/consult/:consultId', 'LINK', 'NAVIGATE', 'INNER', 'appointment.view', 'Free', NULL, 'P4', NULL, false, false, 10),
    ('PATIENT_PORTAL', 'pp.header.profile_switch', NULL, 'Switch family member', NULL, NULL, NULL, 'LINK', 'NAVIGATE', 'HEADER', NULL, 'Starter', NULL, 'P3', NULL, false, true, 120),
    ('PATIENT_PORTAL', 'pp.header.notifications', NULL, 'Notifications', NULL, NULL, '/notifications', 'LINK', 'NAVIGATE', 'HEADER', NULL, 'Free', NULL, 'P3', NULL, false, true, 130),
    ('PATIENT_PORTAL', 'pp.header.language', NULL, 'Language', NULL, NULL, NULL, 'LINK', 'NAVIGATE', 'HEADER', NULL, 'Free', NULL, 'P3', NULL, false, true, 140),
    ('PATIENT_PORTAL', 'pp.account.profile', NULL, 'Profile', NULL, NULL, '/account/profile', 'LINK', 'NAVIGATE', 'USER', NULL, 'Free', NULL, 'P3', NULL, false, true, 150),
    ('PATIENT_PORTAL', 'pp.account.abha', NULL, 'ABHA & identity', NULL, NULL, '/account/abha', 'LINK', 'NAVIGATE', 'USER', NULL, 'Free', NULL, 'P3', NULL, false, true, 160),
    ('PATIENT_PORTAL', 'pp.account.insurance', NULL, 'Insurance', NULL, NULL, '/account/insurance', 'LINK', 'NAVIGATE', 'USER', NULL, 'Starter', NULL, 'P3', NULL, false, true, 170),
    ('PATIENT_PORTAL', 'pp.account.preferences', NULL, 'Language & accessibility', NULL, NULL, '/account/preferences', 'LINK', 'NAVIGATE', 'USER', NULL, 'Free', NULL, 'P3', NULL, false, true, 180),
    ('PATIENT_PORTAL', 'pp.account.notifications', NULL, 'Notification settings', NULL, NULL, '/account/notifications', 'LINK', 'NAVIGATE', 'USER', NULL, 'Free', NULL, 'P3', NULL, false, true, 190),
    ('PATIENT_PORTAL', 'pp.account.security', NULL, 'Security & devices', NULL, NULL, '/account/security', 'LINK', 'NAVIGATE', 'USER', NULL, 'Free', NULL, 'P3', NULL, false, true, 200),
    ('PATIENT_PORTAL', 'pp.account.help', NULL, 'Help & support', NULL, NULL, '/help', 'LINK', 'NAVIGATE', 'USER', NULL, 'Free', NULL, 'P3', NULL, false, true, 210),
    ('PATIENT_PORTAL', 'pp.account.sign_out', NULL, 'Sign out', NULL, NULL, '/logout', 'LINK', 'NAVIGATE', 'USER', NULL, 'Free', NULL, 'P3', NULL, false, true, 220),
    ('KIOSK', 'kiosk.welcome', NULL, 'Welcome', 'Language, large-text / voice mode', NULL, '/', 'LINK', 'NAVIGATE', 'INNER', NULL, 'Starter', NULL, 'P3', NULL, false, false, 10),
    ('KIOSK', 'kiosk.tile.check_in', NULL, 'Check in', 'Identify (phone / ABHA / QR / OTP) → confirm appointment → token', NULL, '/check-in', 'LINK', 'NAVIGATE', 'TILE', NULL, 'Starter', NULL, 'P3', NULL, false, true, 20),
    ('KIOSK', 'kiosk.tile.register', NULL, 'I''m new', 'Self-registration', NULL, '/register', 'LINK', 'NAVIGATE', 'TILE', NULL, 'Starter', NULL, 'P3', NULL, false, true, 30),
    ('KIOSK', 'kiosk.tile.walk_in', NULL, 'Walk-in visit', 'Take a queue token without an appointment', NULL, '/walk-in', 'LINK', 'NAVIGATE', 'TILE', NULL, 'Starter', NULL, 'P3', NULL, false, true, 40),
    ('KIOSK', 'kiosk.tile.pay', NULL, 'Pay a bill', 'Find bill, pay', NULL, '/pay', 'LINK', 'NAVIGATE', 'TILE', NULL, 'Starter', NULL, 'P3', NULL, false, true, 50),
    ('KIOSK', 'kiosk.tile.summary', NULL, 'Print my summary', 'Identity-verified record view and print', NULL, '/summary', 'LINK', 'NAVIGATE', 'TILE', NULL, 'Pro', NULL, 'P3', NULL, false, true, 60),
    ('KIOSK', 'kiosk.identify', NULL, 'Identify', 'Shared identification step', NULL, '/identify', 'LINK', 'NAVIGATE', 'INNER', NULL, 'Starter', NULL, 'P3', NULL, false, false, 70),
    ('KIOSK', 'kiosk.intake', NULL, 'Questions & consent', 'Intake questionnaire, signature consent', NULL, '/intake', 'LINK', 'NAVIGATE', 'INNER', NULL, 'Starter', NULL, 'P3', NULL, false, false, 80),
    ('KIOSK', 'kiosk.token', NULL, 'Your token', 'Token and position', NULL, '/token', 'LINK', 'NAVIGATE', 'INNER', NULL, 'Starter', NULL, 'P3', NULL, false, false, 90),
    ('KIOSK', 'kiosk.display', NULL, 'Queue display', 'Wall-screen now-serving board (display-mode device)', NULL, '/display', 'LINK', 'NAVIGATE', 'INNER', NULL, 'Starter', NULL, 'P3', NULL, false, false, 100),
    ('KIOSK', 'kiosk.setup', NULL, 'Device setup', 'Staff pairing and unlock', NULL, '/setup', 'LINK', 'NAVIGATE', 'INNER', NULL, 'Starter', NULL, 'P3', NULL, false, false, 110),
    ('PLATFORM_ADMIN', 'pa.overview', NULL, 'Overview', 'Signups, verification backlog, health, agent alerts', 'LayoutDashboard', '/dashboard', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P1', NULL, true, true, 10),
    ('PLATFORM_ADMIN', 'pa.tenants', NULL, 'Tenants', 'All participants; filter by type, plan, status', 'Building2', '/tenants', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P1', 'M01', true, true, 20),
    ('PLATFORM_ADMIN', 'pa.verification', NULL, 'Verification queue', 'Review legal profile and credentials; approve / reject', 'BadgeCheck', '/verification', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P1', 'M01', true, true, 30),
    ('PLATFORM_ADMIN', 'pa.providers', NULL, 'Providers', 'Doctor registry, HPR/NMC checks, expiring credentials', 'Stethoscope', '/providers', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P1', 'M14', true, true, 40),
    ('PLATFORM_ADMIN', 'pa.identity', NULL, 'Patient identity', 'Cross-tenant duplicate review, merge audit', 'Fingerprint', '/identity', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P1', 'M02', true, true, 50),
    ('PLATFORM_ADMIN', 'pa.plans', NULL, 'Plans', 'Plans, features, tenant subscriptions', 'Gem', '/plans', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P1', NULL, true, true, 60),
    ('PLATFORM_ADMIN', 'pa.navigation', NULL, 'Navigation', NULL, 'Menu', NULL, 'GROUP', 'EXPAND', 'LEFT', NULL, 'Free', NULL, 'P1', NULL, true, true, 70),
    ('PLATFORM_ADMIN', 'pa.navigation.menu', 'pa.navigation', 'Global menu', 'Global draft editor, preview against a tenant, publish', 'ListTree', '/navigation', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P1', NULL, true, true, 10),
    ('PLATFORM_ADMIN', 'pa.navigation.temporary', 'pa.navigation', 'Announcements', 'Platform-wide temporary menus', 'Megaphone', '/navigation/temporary', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P1', NULL, true, true, 20),
    ('PLATFORM_ADMIN', 'pa.navigation.modules', 'pa.navigation', 'Modules', 'Module registry, approve, suspend, refresh, manifest history', 'Boxes', '/navigation/modules', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P1', NULL, true, true, 30),
    ('PLATFORM_ADMIN', 'pa.imports', NULL, 'Data imports', 'Bulk import jobs across tenants, mapping review', 'Upload', '/imports', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P1', 'M19', true, true, 80),
    ('PLATFORM_ADMIN', 'pa.reference', NULL, 'Reference data', NULL, 'BookOpen', NULL, 'GROUP', 'EXPAND', 'LEFT', NULL, 'Free', NULL, 'P1', 'M19', true, true, 90),
    ('PLATFORM_ADMIN', 'pa.reference.lookups', 'pa.reference', 'Lookups', 'Lookup categories and values', 'List', '/reference/lookups', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P1', NULL, true, true, 10),
    ('PLATFORM_ADMIN', 'pa.reference.terminology', 'pa.reference', 'Terminology', 'ICD, SNOMED, LOINC, drug codes and versions', 'Library', '/reference/terminology', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P2', 'M19', false, true, 20),
    ('PLATFORM_ADMIN', 'pa.reference.formulary', 'pa.reference', 'National formulary', 'Indian generic/brand drug database', 'Pill', '/reference/formulary', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P2', 'M05', false, true, 30),
    ('PLATFORM_ADMIN', 'pa.ai', NULL, 'AI governance', NULL, 'Bot', NULL, 'GROUP', 'EXPAND', 'LEFT', NULL, 'Free', NULL, 'P4', 'M18', false, true, 100),
    ('PLATFORM_ADMIN', 'pa.ai.agents', 'pa.ai', 'Agent registry', 'Purpose, tools, autonomy ceilings, models', 'Bot', '/ai/agents', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P4', 'M18', false, true, 10),
    ('PLATFORM_ADMIN', 'pa.ai.kill_switch', 'pa.ai', 'Kill switches', 'Per agent / tenant / global stop', 'Power', '/ai/kill-switch', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P4', 'M18', false, true, 20),
    ('PLATFORM_ADMIN', 'pa.ai.performance', 'pa.ai', 'Performance', 'Acceptance, correction, escalation; auto-demotion', 'Activity', '/ai/performance', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P4', 'M18', false, true, 30),
    ('PLATFORM_ADMIN', 'pa.ai.budgets', 'pa.ai', 'Budgets', 'Tenant AI spend caps', 'IndianRupee', '/ai/budgets', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P4', 'M18', false, true, 40),
    ('PLATFORM_ADMIN', 'pa.integrations', NULL, 'Integrations', 'ABDM gateway, partner API clients, sandbox', 'Plug', '/integrations', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P1', 'M19', true, true, 110),
    ('PLATFORM_ADMIN', 'pa.compliance', NULL, 'Compliance', NULL, 'ShieldCheck', NULL, 'GROUP', 'EXPAND', 'LEFT', NULL, 'Free', NULL, 'P1', 'M20', true, true, 120),
    ('PLATFORM_ADMIN', 'pa.compliance.audit', 'pa.compliance', 'Audit log', 'Platform-wide audit search', 'ScrollText', '/compliance/audit', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P1', 'M20', true, true, 10),
    ('PLATFORM_ADMIN', 'pa.compliance.incidents', 'pa.compliance', 'Incidents', 'Breach workflow across tenants', 'AlertTriangle', '/compliance/incidents', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P1', 'M20', true, true, 20),
    ('PLATFORM_ADMIN', 'pa.compliance.subprocessors', 'pa.compliance', 'Agreements', 'DPA register, subprocessors', 'FileSignature', '/compliance/agreements', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P1', 'M20', true, true, 30),
    ('PLATFORM_ADMIN', 'pa.tools', NULL, 'Builders', 'Moved from the Provider Console (C3)', 'Wrench', NULL, 'GROUP', 'EXPAND', 'LEFT', NULL, 'Free', NULL, 'P1', NULL, true, true, 130),
    ('PLATFORM_ADMIN', 'pa.tools.workflows', 'pa.tools', 'Workflows', 'Workflow builder (existing platform-ui)', 'Workflow', '/workflows', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P1', NULL, true, true, 10),
    ('PLATFORM_ADMIN', 'pa.tools.forms', 'pa.tools', 'Forms', 'Form builder (existing platform-ui)', 'Layers', '/forms', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P1', NULL, true, true, 20),
    ('PLATFORM_ADMIN', 'pa.notifications', NULL, 'Notification templates', 'Platform message templates and channels', 'Bell', '/notifications/templates', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P1', NULL, true, true, 140),
    ('PLATFORM_ADMIN', 'pa.operators', NULL, 'Operators', 'CliniqAI staff accounts (existing platform-ui)', 'UserCog', '/admin/users', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P1', NULL, true, true, 150),
    ('PLATFORM_ADMIN', 'pa.settings', NULL, 'Settings', 'Platform profile, storage, security (existing platform-ui settings)', 'Settings', '/settings', 'LINK', 'NAVIGATE', 'LEFT', NULL, 'Free', NULL, 'P1', NULL, true, true, 160),
    ('PLATFORM_ADMIN', 'pa.header.environment', NULL, 'Environment badge', NULL, NULL, NULL, 'LINK', 'NAVIGATE', 'HEADER', NULL, 'Free', NULL, 'P1', NULL, true, true, 170),
    ('PLATFORM_ADMIN', 'pa.header.search', NULL, 'Search tenants and users', NULL, NULL, NULL, 'LINK', 'NAVIGATE', 'HEADER', NULL, 'Free', NULL, 'P1', NULL, true, true, 180),
    ('PLATFORM_ADMIN', 'pa.header.notifications', NULL, 'Notifications', NULL, NULL, '/notifications', 'LINK', 'NAVIGATE', 'HEADER', NULL, 'Free', NULL, 'P1', NULL, true, true, 190),
    ('PLATFORM_ADMIN', 'pa.user.profile', NULL, 'My profile', NULL, NULL, '/settings/profile', 'LINK', 'NAVIGATE', 'USER', NULL, 'Free', NULL, 'P1', NULL, true, true, 200),
    ('PLATFORM_ADMIN', 'pa.user.sign_out', NULL, 'Sign out', NULL, NULL, '/logout', 'LINK', 'NAVIGATE', 'USER', NULL, 'Free', NULL, 'P1', NULL, true, true, 210),
    ('PLATFORM_ADMIN', 'pa.tenants.detail', 'pa.tenants', 'Tenant', NULL, NULL, '/tenants/:id', 'LINK', 'NAVIGATE', 'INNER', NULL, 'Free', NULL, 'P1', NULL, true, false, 10),
    ('PLATFORM_ADMIN', 'pa.tenants.detail.profile', 'pa.tenants.detail', 'Profile & verification', NULL, NULL, '/tenants/:id', 'LINK', 'NAVIGATE', 'TAB', NULL, 'Free', NULL, 'P1', NULL, true, false, 10),
    ('PLATFORM_ADMIN', 'pa.tenants.detail.users', 'pa.tenants.detail', 'Users', NULL, NULL, '/tenants/:id/users', 'LINK', 'NAVIGATE', 'TAB', NULL, 'Free', NULL, 'P1', NULL, true, false, 20),
    ('PLATFORM_ADMIN', 'pa.tenants.detail.subscription', 'pa.tenants.detail', 'Subscription', NULL, NULL, '/tenants/:id/subscription', 'LINK', 'NAVIGATE', 'TAB', NULL, 'Free', NULL, 'P1', NULL, true, false, 30),
    ('PLATFORM_ADMIN', 'pa.tenants.detail.menu', 'pa.tenants.detail', 'Menu', NULL, NULL, '/tenants/:id/menu', 'LINK', 'NAVIGATE', 'TAB', NULL, 'Free', NULL, 'P1', NULL, true, false, 40),
    ('PLATFORM_ADMIN', 'pa.tenants.detail.audit', 'pa.tenants.detail', 'Audit', NULL, NULL, '/tenants/:id/audit', 'LINK', 'NAVIGATE', 'TAB', NULL, 'Free', NULL, 'P1', NULL, true, false, 50),
    ('PLATFORM_ADMIN', 'pa.verification.review', 'pa.verification', 'Review submission', NULL, NULL, '/verification/:tenantId', 'LINK', 'NAVIGATE', 'INNER', NULL, 'Free', NULL, 'P1', NULL, true, false, 10),
    ('PLATFORM_ADMIN', 'pa.navigation.modules.detail', 'pa.navigation.modules', 'Module', NULL, NULL, '/navigation/modules/:code', 'LINK', 'NAVIGATE', 'INNER', NULL, 'Free', NULL, 'P1', NULL, true, false, 10),
    ('PLATFORM_ADMIN', 'pa.imports.detail', 'pa.imports', 'Import job', NULL, NULL, '/imports/:jobId', 'LINK', 'NAVIGATE', 'INNER', NULL, 'Free', NULL, 'P1', NULL, true, false, 10),
    ('PLATFORM_ADMIN', 'pa.ai.agents.detail', 'pa.ai.agents', 'Agent', NULL, NULL, '/ai/agents/:agentId', 'LINK', 'NAVIGATE', 'INNER', NULL, 'Free', NULL, 'P4', NULL, false, false, 10),
    ('PLATFORM_ADMIN', 'pa.settings.profile', 'pa.settings', 'Profile', NULL, NULL, '/settings/profile', 'LINK', 'NAVIGATE', 'SETTINGS', NULL, 'Free', NULL, 'P1', NULL, true, true, 10),
    ('PLATFORM_ADMIN', 'pa.settings.tenant', 'pa.settings', 'Platform organisation', NULL, NULL, '/settings/tenant', 'LINK', 'NAVIGATE', 'SETTINGS', NULL, 'Free', NULL, 'P1', NULL, true, true, 20),
    ('PLATFORM_ADMIN', 'pa.settings.storage', 'pa.settings', 'Storage', NULL, NULL, '/settings/storage', 'LINK', 'NAVIGATE', 'SETTINGS', NULL, 'Free', NULL, 'P1', NULL, true, true, 30),
    ('PLATFORM_ADMIN', 'pa.settings.notifications', 'pa.settings', 'Notifications', NULL, NULL, '/settings/notifications', 'LINK', 'NAVIGATE', 'SETTINGS', NULL, 'Free', NULL, 'P1', NULL, true, true, 40),
    ('PLATFORM_ADMIN', 'pa.settings.billing', 'pa.settings', 'Billing', NULL, NULL, '/settings/billing', 'LINK', 'NAVIGATE', 'SETTINGS', NULL, 'Free', NULL, 'P1', NULL, true, true, 50);

-- A privilege that does not exist would silently leave an item ungated: fail instead.
DO $$
DECLARE missing TEXT;
BEGIN
    SELECT string_agg(DISTINCT s.privilege, ', ') INTO missing
      FROM seed_menu s LEFT JOIN privileges p ON p.code = s.privilege
     WHERE s.privilege IS NOT NULL AND p.id IS NULL;
    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'menu catalogue seed references unknown privileges: %', missing;
    END IF;
END $$;

-- Existing global rows (the V27 patient-portal items) are updated; the rest are inserted.
UPDATE menu_items m
   SET label = s.label, description = s.description, icon = s.icon, route = s.route,
       type = s.type, behavior = s.behavior, placement = s.placement,
       tenant_types = s.tenant_types, sort_order = s.sort_order,
       is_active = s.is_active, is_visible = s.is_visible,
       required_privilege_id = (SELECT id FROM privileges WHERE code = s.privilege),
       metadata = jsonb_strip_nulls(jsonb_build_object('phase', s.phase, 'tier', s.tier, 'module', s.module)),
       updated_at = now()
  FROM seed_menu s
  JOIN applications a ON a.code = s.app
 WHERE m.application_id = a.id AND m.code = s.code AND m.tenant_id IS NULL AND m.deleted_at IS NULL;

INSERT INTO menu_items (application_id, code, label, description, icon, route, type, behavior, placement,
                        tenant_types, sort_order, is_active, is_visible, required_privilege_id, metadata, depth, path)
SELECT a.id, s.code, s.label, s.description, s.icon, s.route, s.type, s.behavior, s.placement,
       s.tenant_types, s.sort_order, s.is_active, s.is_visible,
       (SELECT id FROM privileges WHERE code = s.privilege),
       jsonb_strip_nulls(jsonb_build_object('phase', s.phase, 'tier', s.tier, 'module', s.module)),
       0, s.code
  FROM seed_menu s
  JOIN applications a ON a.code = s.app
 WHERE NOT EXISTS (SELECT 1 FROM menu_items m
                    WHERE m.application_id = a.id AND m.code = s.code AND m.tenant_id IS NULL AND m.deleted_at IS NULL);

-- Hierarchy: parent, then depth and materialised path from the roots down.
UPDATE menu_items c
   SET parent_id = p.id
  FROM seed_menu s
  JOIN applications a ON a.code = s.app
  JOIN menu_items p ON p.application_id = a.id AND p.code = s.parent_code AND p.tenant_id IS NULL AND p.deleted_at IS NULL
 WHERE c.application_id = a.id AND c.code = s.code AND c.tenant_id IS NULL AND c.deleted_at IS NULL;

WITH RECURSIVE tree AS (
    SELECT m.id, 0 AS depth, m.code::text AS path
      FROM menu_items m
      JOIN applications a ON a.id = m.application_id
      JOIN seed_menu s ON s.app = a.code AND s.code = m.code AND s.parent_code IS NULL
     WHERE m.tenant_id IS NULL AND m.deleted_at IS NULL
    UNION ALL
    SELECT c.id, t.depth + 1, t.path || '/' || c.code
      FROM tree t JOIN menu_items c ON c.parent_id = t.id
     WHERE c.deleted_at IS NULL
)
UPDATE menu_items m SET depth = t.depth, path = t.path FROM tree t WHERE m.id = t.id;

-- Plan access from the tier (E4).
DELETE FROM menu_plan_access WHERE menu_item_id IN (
    SELECT m.id FROM menu_items m JOIN applications a ON a.id = m.application_id
      JOIN seed_menu s ON s.app = a.code AND s.code = m.code WHERE m.tenant_id IS NULL AND m.deleted_at IS NULL);

INSERT INTO menu_plan_access (menu_item_id, plan)
SELECT m.id, pl.plan
  FROM seed_menu s
  JOIN applications a ON a.code = s.app
  JOIN menu_items m ON m.application_id = a.id AND m.code = s.code AND m.tenant_id IS NULL AND m.deleted_at IS NULL
  JOIN (VALUES ('Free','free'),('Free','starter'),('Free','pro'),('Free','enterprise'),
               ('Starter','starter'),('Starter','pro'),('Starter','enterprise'),
               ('Pro','pro'),('Pro','enterprise'),
               ('Paid','enterprise')) AS pl(tier, plan) ON pl.tier = s.tier;

-- Role access: every system role that holds the privilege (all roles of the app when ungated).
DELETE FROM menu_role_access WHERE menu_item_id IN (
    SELECT m.id FROM menu_items m JOIN applications a ON a.id = m.application_id
      JOIN seed_menu s ON s.app = a.code AND s.code = m.code WHERE m.tenant_id IS NULL AND m.deleted_at IS NULL);

INSERT INTO menu_role_access (menu_item_id, role_id)
SELECT m.id, r.id
  FROM seed_menu s
  JOIN applications a ON a.code = s.app
  JOIN menu_items m ON m.application_id = a.id AND m.code = s.code AND m.tenant_id IS NULL AND m.deleted_at IS NULL
  JOIN roles r ON r.tenant_id IS NULL
              AND ((s.app = 'PROVIDER_CONSOLE' AND r.code <> 'PATIENT') OR (s.app = 'PATIENT_PORTAL' AND r.code = 'PATIENT'))
 WHERE s.privilege IS NULL
    OR EXISTS (SELECT 1 FROM role_privileges rp JOIN privileges p ON p.id = rp.privilege_id
                WHERE rp.role_id = r.id AND p.code = s.privilege)
ON CONFLICT DO NOTHING;
