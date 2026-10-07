package com.is2c.hrm.org.api;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Employee number format (FR-014): literal text plus tokens {COMPANY}, {BRANCH}, {YYYY}, {SEQ:n} (1..10).
 * Exactly one {SEQ:n} is required.
 */
public final class EmployeeNumberFormat {

    private static final Pattern TOKEN = Pattern.compile("\\{([A-Z]+)(?::(\\d+))?}");

    private final String pattern;
    private final boolean usesBranch;
    private final boolean usesYear;

    private EmployeeNumberFormat(String pattern, boolean usesBranch, boolean usesYear) {
        this.pattern = pattern;
        this.usesBranch = usesBranch;
        this.usesYear = usesYear;
    }

    /** Returns the list of problems; empty when valid. */
    public static List<String> problems(String pattern) {
        List<String> problems = new ArrayList<>();
        if (pattern == null || pattern.isBlank()) {
            problems.add("format is required");
            return problems;
        }
        if (pattern.length() > 60) {
            problems.add("format is too long");
        }
        int seqCount = 0;
        Matcher m = TOKEN.matcher(pattern);
        while (m.find()) {
            String name = m.group(1);
            String arg = m.group(2);
            switch (name) {
                case "COMPANY", "BRANCH", "YYYY" -> {
                    if (arg != null) {
                        problems.add("{" + name + "} takes no argument");
                    }
                }
                case "SEQ" -> {
                    seqCount++;
                    int width = arg == null ? 0 : Integer.parseInt(arg);
                    if (width < 1 || width > 10) {
                        problems.add("{SEQ:n} width must be between 1 and 10");
                    }
                }
                default -> problems.add("unknown token {" + name + "}");
            }
        }
        if (seqCount != 1) {
            problems.add("format must contain exactly one {SEQ:n} token");
        }
        String stripped = TOKEN.matcher(pattern).replaceAll("");
        if (stripped.contains("{") || stripped.contains("}")) {
            problems.add("format contains a malformed token");
        }
        return problems;
    }

    public static EmployeeNumberFormat parse(String pattern) {
        List<String> problems = problems(pattern);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
        return new EmployeeNumberFormat(pattern, pattern.contains("{BRANCH}"), pattern.contains("{YYYY}"));
    }

    /** Counter key so that sequences restart per branch/year when those tokens are present. */
    public String sequenceKey(String branchCode, int year) {
        return "EMPNO" + (usesBranch ? ":" + branchCode : "") + (usesYear ? ":" + year : "");
    }

    public String render(String companyCode, String branchCode, int year, long sequence) {
        Matcher m = TOKEN.matcher(pattern);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String replacement = switch (m.group(1)) {
                case "COMPANY" -> companyCode;
                case "BRANCH" -> branchCode;
                case "YYYY" -> String.valueOf(year);
                case "SEQ" -> String.format("%0" + m.group(2) + "d", sequence);
                default -> throw new IllegalStateException();
            };
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
}
