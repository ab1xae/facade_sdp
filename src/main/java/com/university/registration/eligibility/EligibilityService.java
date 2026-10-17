package com.university.registration.eligibility;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class EligibilityService {

    private final EligibilityRepository repository;
    private final PrerequisiteChecker prerequisites;
    private final FinancialHoldChecker holds;

    public EligibilityService(EligibilityRepository repository, PrerequisiteChecker prerequisites, FinancialHoldChecker holds) {
        this.repository = repository;
        this.prerequisites = prerequisites;
        this.holds = holds;
    }

    public EnrollmentRequest submit(String reference, String studentId, String courseCode) {
        StudentRecord student = repository.findStudent(studentId)
                .orElseThrow(() -> new EligibilityException("Unknown student " + studentId));
        EnrollmentRequest request = EnrollmentRequest.open(reference, studentId, courseCode);
        Optional<String> rejection = rejectionReason(student, courseCode);
        if (rejection.isPresent()) {
            request.reject(rejection.get());
            repository.save(request);
            throw new EligibilityException(rejection.get());
        }
        request.approve();
        return repository.save(request);
    }

    private Optional<String> rejectionReason(StudentRecord student, String courseCode) {
        Optional<String> hold = holds.blockingHold(student);
        if (hold.isPresent()) {
            return hold;
        }
        if (prerequisites.alreadyCompleted(courseCode, student.completedCourses())) {
            return Optional.of(student.id() + " has already completed " + courseCode);
        }
        Set<String> missing = prerequisites.missing(repository.prerequisitesOf(courseCode), student.completedCourses());
        if (!missing.isEmpty()) {
            return Optional.of("Missing prerequisites for " + courseCode + ": " + String.join(", ", missing));
        }
        if (repository.hasApproved(student.id(), courseCode)) {
            return Optional.of(student.id() + " is already registered for " + courseCode);
        }
        return Optional.empty();
    }

    public Optional<EnrollmentRequest> cancel(String reference, String reason) {
        return approvedRequest(reference).map(request -> {
            request.cancel(reason);
            return repository.save(request);
        });
    }

    public Optional<EnrollmentRequest> approvedRequest(String reference) {
        return repository.latestByReference(reference)
                .filter(request -> request.status() == EnrollmentRequest.Status.APPROVED);
    }

    public Optional<EnrollmentRequest> requestFor(String reference) {
        return repository.latestByReference(reference);
    }

    public void placeHold(String studentId, String reason) {
        repository.updateFinancialHold(studentId, reason);
    }

    public void liftHold(String studentId) {
        repository.updateFinancialHold(studentId, null);
    }

    public Optional<StudentRecord> student(String studentId) {
        return repository.findStudent(studentId);
    }

    public List<StudentRecord> students() {
        return repository.students();
    }

    public List<EnrollmentRequest> requests() {
        return repository.requests();
    }
}
