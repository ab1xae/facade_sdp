package com.university.registration.eligibility;

import java.util.Set;
import java.util.TreeSet;

public final class PrerequisiteChecker {

    public Set<String> missing(Set<String> required, Set<String> completed) {
        Set<String> missing = new TreeSet<>(required);
        missing.removeAll(completed);
        return missing;
    }

    public boolean alreadyCompleted(String courseCode, Set<String> completed) {
        return completed.contains(courseCode);
    }
}
