package com.university.registration.records;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TranscriptService {

    private final RecordsRepository repository;

    public TranscriptService(RecordsRepository repository) {
        this.repository = repository;
    }

    public TranscriptEntry record(String reference, String studentId, String courseCode, String term) {
        if (!repository.isTermOpen(term)) {
            throw new FinalizationException("Transcript for term " + term + " is locked; " + courseCode + " not recorded");
        }
        return repository.addTranscriptEntry(reference, studentId, courseCode, term);
    }

    public Optional<TranscriptEntry> removeFor(String reference) {
        return repository.transcriptEntryFor(reference).map(entry -> {
            repository.updateTranscriptStatus(entry.id(), TranscriptEntry.REMOVED);
            return new TranscriptEntry(entry.id(), entry.reference(), entry.studentId(), entry.courseCode(),
                    entry.term(), TranscriptEntry.REMOVED);
        });
    }

    public Optional<TranscriptEntry> entryFor(String reference) {
        return repository.transcriptEntryFor(reference);
    }

    public void openTerm(String term) {
        repository.saveTerm(term, true);
    }

    public void lockTerm(String term) {
        repository.saveTerm(term, false);
    }

    public boolean isOpen(String term) {
        return repository.isTermOpen(term);
    }

    public Map<String, Boolean> terms() {
        return repository.terms();
    }

    public List<TranscriptEntry> entries() {
        return repository.transcript();
    }
}
