package com.is2c.hrm.security.service;

import java.util.ArrayList;
import java.util.List;

import com.is2c.hrm.common.error.FieldError;
import com.is2c.hrm.common.error.ValidationException;

/** FR-003 / CL-E3 password rules. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;

    private PasswordPolicy() {
    }

    public static List<String> violations(String password) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH) {
            problems.add("must be at least " + MIN_LENGTH + " characters");
        }
        String p = password == null ? "" : password;
        if (p.chars().noneMatch(Character::isUpperCase)) {
            problems.add("must contain an upper-case letter");
        }
        if (p.chars().noneMatch(Character::isLowerCase)) {
            problems.add("must contain a lower-case letter");
        }
        if (p.chars().noneMatch(Character::isDigit)) {
            problems.add("must contain a digit");
        }
        if (p.chars().allMatch(Character::isLetterOrDigit)) {
            problems.add("must contain a non-alphanumeric character");
        }
        return problems;
    }

    public static void check(String field, String password) {
        List<String> problems = violations(password);
        if (!problems.isEmpty()) {
            throw new ValidationException("Password does not meet the policy",
                    problems.stream().map(m -> new FieldError(field, m)).toList());
        }
    }
}
