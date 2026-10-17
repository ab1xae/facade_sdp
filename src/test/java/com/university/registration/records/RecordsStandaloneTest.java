package com.university.registration.records;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordsStandaloneTest {

    @TempDir
    Path dataDir;

    private BillingSystem billing;
    private TranscriptService transcript;

    @BeforeEach
    void freshStore() {
        RecordsRepository repository = new RecordsRepository(dataDir);
        repository.saveAccount(new StudentAccount("S1", 0, 500_000, 0));
        repository.saveAccount(new StudentAccount("S2", 0, 500_000, 50));
        repository.saveAccount(new StudentAccount("S3", 380_000, 400_000, 0));
        repository.saveTerm("2026-FALL", true);
        billing = new BillingSystem(repository, new TuitionCalculator());
        transcript = new TranscriptService(repository);
    }

    private BillingSystem billingAfterRestart() {
        return new BillingSystem(new RecordsRepository(dataDir), new TuitionCalculator());
    }

    @Test
    void issuingAnInvoiceChargesTheAccountDurably() {
        Invoice invoice = billing.issue("REF-1", "S1", "CS201", 5);

        assertEquals(125_000, invoice.amount());
        assertEquals(125_000, billingAfterRestart().account("S1").orElseThrow().balance());
        assertEquals(Invoice.Status.ISSUED, billingAfterRestart().invoiceFor("REF-1").orElseThrow().status());
    }

    @Test
    void scholarshipReducesTuition() {
        assertEquals(62_500, billing.issue("REF-1", "S2", "CS201", 5).amount());
    }

    @Test
    void creditLimitRejectsWithoutCharging() {
        FinalizationException error = assertThrows(FinalizationException.class,
                () -> billing.issue("REF-1", "S3", "CS201", 5));

        assertTrue(error.getMessage().startsWith("Billing rejected"));
        assertEquals(380_000, billingAfterRestart().account("S3").orElseThrow().balance());
        assertTrue(billing.invoices().isEmpty());
    }

    @Test
    void offlineBillingAndMissingAccountReject() {
        billing.setOnline(false);
        assertThrows(FinalizationException.class, () -> billing.issue("REF-1", "S1", "CS201", 5));
        assertFalse(billingAfterRestart().isOnline());

        billing.setOnline(true);
        assertThrows(FinalizationException.class, () -> billing.issue("REF-2", "S9", "CS201", 5));
    }

    @Test
    void voidingRestoresTheBalanceAndIsIdempotent() {
        billing.issue("REF-1", "S1", "CS201", 5);

        assertEquals(Invoice.Status.VOIDED, billing.voidFor("REF-1").orElseThrow().status());
        assertTrue(billing.voidFor("REF-1").isEmpty());
        assertEquals(0, billingAfterRestart().account("S1").orElseThrow().balance());
        assertEquals(1, billing.invoices().size());
    }

    @Test
    void paidInvoiceCannotBeVoided() {
        billing.issue("REF-1", "S1", "CS201", 5);

        assertEquals(Invoice.Status.PAID, billing.pay("REF-1").status());
        assertTrue(billing.voidFor("REF-1").isEmpty());
        assertThrows(IllegalStateException.class, () -> billing.pay("REF-1"));
        assertThrows(FinalizationException.class, () -> billing.pay("REF-404"));
        assertEquals(0, billing.account("S1").orElseThrow().balance());
        assertEquals(3, billing.accounts().size());
    }

    @Test
    void transcriptRecordsOnlyInOpenTermsAndCanBeRemoved() {
        TranscriptEntry entry = transcript.record("REF-1", "S1", "CS201", "2026-FALL");
        transcript.lockTerm("2026-FALL");

        assertEquals(TranscriptEntry.REGISTERED, entry.status());
        assertThrows(FinalizationException.class, () -> transcript.record("REF-2", "S2", "CS201", "2026-FALL"));
        assertThrows(FinalizationException.class, () -> transcript.record("REF-3", "S2", "CS201", "1999-SPRING"));
        assertEquals(Map.of("2026-FALL", false), new TranscriptService(new RecordsRepository(dataDir)).terms());

        assertEquals(TranscriptEntry.REMOVED, transcript.removeFor("REF-1").orElseThrow().status());
        assertTrue(transcript.removeFor("REF-1").isEmpty());
        assertTrue(transcript.entryFor("REF-1").isEmpty());
        transcript.openTerm("2026-FALL");
        assertTrue(transcript.isOpen("2026-FALL"));
        assertEquals(1, transcript.entries().size());
    }

    @Test
    void invoiceEnforcesItsOwnLegalTransitions() {
        Invoice invoice = Invoice.issue("REF-1", "S1", "CS201", 1);
        invoice.voidInvoice();

        assertThrows(IllegalStateException.class, invoice::pay);
        assertThrows(IllegalStateException.class, invoice::voidInvoice);
        assertThrows(IllegalArgumentException.class, () -> new TuitionCalculator().tuitionFor(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TuitionCalculator().tuitionFor(3, 101));
        assertTrue(invoice.toString().contains("VOIDED"));
        assertTrue(new StudentAccount("S1", 0, 1, 0).toString().contains("S1"));
    }
}
