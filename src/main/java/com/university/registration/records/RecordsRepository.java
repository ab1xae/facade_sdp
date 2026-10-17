package com.university.registration.records;

import com.university.registration.storage.SqliteStore;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class RecordsRepository {

    public static final String FILE_NAME = "records.db";

    private final SqliteStore store;

    public RecordsRepository(Path dataDir) {
        store = new SqliteStore(dataDir.resolve(FILE_NAME),
                "CREATE TABLE IF NOT EXISTS accounts (student_id TEXT PRIMARY KEY, balance INTEGER NOT NULL, "
                        + "credit_limit INTEGER NOT NULL, scholarship_percent INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS invoices (id INTEGER PRIMARY KEY AUTOINCREMENT, reference TEXT NOT NULL, "
                        + "student_id TEXT NOT NULL, course_code TEXT NOT NULL, amount INTEGER NOT NULL, status TEXT NOT NULL)",
                "CREATE TABLE IF NOT EXISTS transcript (id INTEGER PRIMARY KEY AUTOINCREMENT, reference TEXT NOT NULL, "
                        + "student_id TEXT NOT NULL, course_code TEXT NOT NULL, term TEXT NOT NULL, status TEXT NOT NULL)",
                "CREATE TABLE IF NOT EXISTS terms (term TEXT PRIMARY KEY, open INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS flags (name TEXT PRIMARY KEY, enabled INTEGER NOT NULL)");
    }

    public void saveAccount(StudentAccount account) {
        store.execute("INSERT OR REPLACE INTO accounts (student_id, balance, credit_limit, scholarship_percent) "
                        + "VALUES (?, ?, ?, ?)",
                account.studentId(), account.balance(), account.creditLimit(), account.scholarshipPercent());
    }

    public Optional<StudentAccount> findAccount(String studentId) {
        return store.first("SELECT * FROM accounts WHERE student_id = ?", RecordsRepository::account, studentId);
    }

    public List<StudentAccount> accounts() {
        return store.list("SELECT * FROM accounts ORDER BY student_id", RecordsRepository::account);
    }

    public Invoice save(Invoice invoice, long balanceChange) {
        return store.inTransaction(session -> {
            if (invoice.id() == null) {
                invoice.assignId(session.insert(
                        "INSERT INTO invoices (reference, student_id, course_code, amount, status) VALUES (?, ?, ?, ?, ?)",
                        invoice.reference(), invoice.studentId(), invoice.courseCode(), invoice.amount(),
                        invoice.status().name()));
            } else {
                session.execute("UPDATE invoices SET status = ? WHERE id = ?", invoice.status().name(), invoice.id());
            }
            session.execute("UPDATE accounts SET balance = balance + ? WHERE student_id = ?",
                    balanceChange, invoice.studentId());
            return invoice;
        });
    }

    public Optional<Invoice> invoiceFor(String reference) {
        return store.first("SELECT * FROM invoices WHERE reference = ? AND status <> 'VOIDED' ORDER BY id DESC LIMIT 1",
                RecordsRepository::invoice, reference);
    }

    public List<Invoice> invoices() {
        return store.list("SELECT * FROM invoices ORDER BY id", RecordsRepository::invoice);
    }

    public TranscriptEntry addTranscriptEntry(String reference, String studentId, String courseCode, String term) {
        long id = store.insert("INSERT INTO transcript (reference, student_id, course_code, term, status) VALUES (?, ?, ?, ?, ?)",
                reference, studentId, courseCode, term, TranscriptEntry.REGISTERED);
        return new TranscriptEntry(id, reference, studentId, courseCode, term, TranscriptEntry.REGISTERED);
    }

    public void updateTranscriptStatus(long id, String status) {
        store.execute("UPDATE transcript SET status = ? WHERE id = ?", status, id);
    }

    public Optional<TranscriptEntry> transcriptEntryFor(String reference) {
        return store.first("SELECT * FROM transcript WHERE reference = ? AND status = ? ORDER BY id DESC LIMIT 1",
                RecordsRepository::entry, reference, TranscriptEntry.REGISTERED);
    }

    public List<TranscriptEntry> transcript() {
        return store.list("SELECT * FROM transcript ORDER BY id", RecordsRepository::entry);
    }

    public void saveTerm(String term, boolean open) {
        store.execute("INSERT OR REPLACE INTO terms (term, open) VALUES (?, ?)", term, open ? 1 : 0);
    }

    public boolean isTermOpen(String term) {
        return store.first("SELECT open FROM terms WHERE term = ?", row -> row.getInt(1) == 1, term).orElse(false);
    }

    public Map<String, Boolean> terms() {
        Map<String, Boolean> terms = new TreeMap<>();
        store.list("SELECT term, open FROM terms", row -> terms.put(row.getString(1), row.getInt(2) == 1));
        return terms;
    }

    public void saveFlag(String name, boolean enabled) {
        store.execute("INSERT OR REPLACE INTO flags (name, enabled) VALUES (?, ?)", name, enabled ? 1 : 0);
    }

    public boolean flag(String name, boolean fallback) {
        return store.first("SELECT enabled FROM flags WHERE name = ?", row -> row.getInt(1) == 1, name).orElse(fallback);
    }

    private static StudentAccount account(ResultSet row) throws SQLException {
        return new StudentAccount(row.getString("student_id"), row.getLong("balance"), row.getLong("credit_limit"),
                row.getInt("scholarship_percent"));
    }

    private static Invoice invoice(ResultSet row) throws SQLException {
        return new Invoice(row.getLong("id"), row.getString("reference"), row.getString("student_id"),
                row.getString("course_code"), row.getLong("amount"), Invoice.Status.valueOf(row.getString("status")));
    }

    private static TranscriptEntry entry(ResultSet row) throws SQLException {
        return new TranscriptEntry(row.getLong("id"), row.getString("reference"), row.getString("student_id"),
                row.getString("course_code"), row.getString("term"), row.getString("status"));
    }
}
