package com.university.registration.records;

import java.util.List;
import java.util.Optional;

public final class BillingSystem {

    static final String ONLINE_FLAG = "billing_online";

    private final RecordsRepository repository;
    private final TuitionCalculator calculator;

    public BillingSystem(RecordsRepository repository, TuitionCalculator calculator) {
        this.repository = repository;
        this.calculator = calculator;
    }

    public Invoice issue(String reference, String studentId, String courseCode, int credits) {
        if (!isOnline()) {
            throw new FinalizationException("Billing system is offline; invoice for " + courseCode + " not issued");
        }
        StudentAccount account = repository.findAccount(studentId)
                .orElseThrow(() -> new FinalizationException("No billing account for " + studentId));
        long amount = calculator.tuitionFor(credits, account.scholarshipPercent());
        if (!calculator.fitsCreditLimit(account, amount)) {
            throw new FinalizationException(String.format(
                    "Billing rejected: balance %,d + tuition %,d exceeds credit limit %,d KZT",
                    account.balance(), amount, account.creditLimit()));
        }
        return repository.save(Invoice.issue(reference, studentId, courseCode, amount), amount);
    }

    public Optional<Invoice> voidFor(String reference) {
        return repository.invoiceFor(reference)
                .filter(invoice -> invoice.status() == Invoice.Status.ISSUED)
                .map(invoice -> {
                    invoice.voidInvoice();
                    return repository.save(invoice, -invoice.amount());
                });
    }

    public Invoice pay(String reference) {
        Invoice invoice = repository.invoiceFor(reference)
                .orElseThrow(() -> new FinalizationException("No invoice for " + reference));
        invoice.pay();
        return repository.save(invoice, -invoice.amount());
    }

    public Optional<Invoice> invoiceFor(String reference) {
        return repository.invoiceFor(reference);
    }

    public void setOnline(boolean online) {
        repository.saveFlag(ONLINE_FLAG, online);
    }

    public boolean isOnline() {
        return repository.flag(ONLINE_FLAG, true);
    }

    public Optional<StudentAccount> account(String studentId) {
        return repository.findAccount(studentId);
    }

    public List<StudentAccount> accounts() {
        return repository.accounts();
    }

    public List<Invoice> invoices() {
        return repository.invoices();
    }
}
