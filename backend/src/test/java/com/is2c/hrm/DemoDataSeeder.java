package com.is2c.hrm;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.is2c.hrm.security.api.ScopeType;
import com.is2c.hrm.security.domain.AppUser;
import com.is2c.hrm.security.domain.AppUserRepository;
import com.is2c.hrm.security.domain.Role;
import com.is2c.hrm.security.domain.RoleRepository;
import com.is2c.hrm.security.service.PrincipalLoader;
import com.is2c.hrm.security.service.TokenService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Sample data for manual testing ({@link DevHrmApplication} with {@code -Ddev.seed=true}). Builds two companies
 * through the public REST API, so everything passes the same validation, audit and approval paths as real use:
 * org structure, holidays, shifts, ~55 employees with salaries, ~6 weeks of punches up to today, approved and
 * pending leave, OT approvals, a published roster for next week, a processed payroll for last month, and demo
 * users (all {@value #PASSWORD}, no forced password change). Runs only on a database without companies.
 * Deterministic: every run on a fresh database produces the same people.
 */
final class DemoDataSeeder {

    static final String PASSWORD = "Demo#Pass2026";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String[] MALE = {"Kasun", "Chamara", "Ruwan", "Tharindu", "Malith", "Nuwan", "Lahiru",
            "Dinesh", "Sampath", "Asela", "Pradeep", "Supun", "Isuru", "Janaka", "Mahesh", "Roshan", "Thilina", "Saman"};
    private static final String[] FEMALE = {"Dilani", "Ishara", "Sachini", "Anushka", "Hasini", "Anjali", "Nadeesha",
            "Chathurika", "Madhavi", "Sanduni", "Nirmala", "Kavindi", "Upeksha", "Ruvini", "Tharushi", "Gayani"};
    private static final String[] LAST = {"Perera", "Fernando", "Silva", "Jayasinghe", "Bandara", "Wickramasinghe",
            "Rathnayake", "Gunawardena", "Herath", "Peiris", "Abeysekara", "Wijesinghe", "Dissanayake", "Senanayake",
            "Karunaratne", "Ekanayake", "Mendis", "Samarasinghe", "Liyanage", "Weerasinghe"};

    /** A department: its head (a manager with a user account) plus {@code staff} people reporting to them. */
    private record Dept(String code, String name, String parent, String branch, String headDesig, String staffDesig,
            int staff, String shift, int basicFrom, int basicTo) {
    }

    private record Person(String id, String badge, String shift, String managerId, String deptCode, LocalDate join) {
    }

    private final RestClient http;
    private final JsonMapper json;
    private final Clock clock;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final PrincipalLoader principals;
    private final TransactionTemplate tx;
    private final Random rnd = new Random(42);
    private final Map<String, String> credentials = new LinkedHashMap<>();
    private AppUser admin;
    private AppUser actor;

    private DemoDataSeeder(ConfigurableApplicationContext ctx) {
        String port = ctx.getEnvironment().getProperty("local.server.port", "8080");
        this.http = RestClient.builder().baseUrl("http://localhost:" + port + "/api/v1").build();
        this.json = ctx.getBean(JsonMapper.class);
        this.clock = ctx.getBean(Clock.class);
        this.users = ctx.getBean(AppUserRepository.class);
        this.roles = ctx.getBean(RoleRepository.class);
        this.encoder = ctx.getBean(PasswordEncoder.class);
        this.tokens = ctx.getBean(TokenService.class);
        this.principals = ctx.getBean(PrincipalLoader.class);
        this.tx = ctx.getBean(TransactionTemplate.class);
    }

    static void seed(ConfigurableApplicationContext ctx) {
        new DemoDataSeeder(ctx).run();
    }

    private void run() {
        if (users.findByUsernameIgnoreCase("demo.admin").isPresent()) {
            log.info("Demo data already present (user demo.admin exists) — skipping. Use -Ddev.clean=true to reseed.");
            return;
        }
        long start = System.nanoTime();
        admin = tx.execute(s -> {
            AppUser u = new AppUser("demo.admin", "Demo Administrator", encoder.encode(PASSWORD));
            u.setScopeType(ScopeType.ALL);
            u.getRoles().add(roles.findByCode(Role.SUPER_ADMIN).orElseThrow());
            return users.save(u);
        });
        actor = admin;
        credentials.put("demo.admin", "Super Administrator");
        company("ACME", "Acme Holdings (Pvt) Ltd", "A", List.of(
                new Dept("OPS", "Operations", null, "CMB", "OPM", "MOP", 13, "DAY", 60_000, 110_000),
                new Dept("ENG", "Engineering", null, "CMB", "ENL", "SE", 0, "FLEX", 0, 0),
                new Dept("ENG-SW", "Software Development", "ENG", "CMB", "ENL", "SE", 8, "FLEX", 140_000, 260_000),
                new Dept("ENG-QA", "Quality Assurance", "ENG", "CMB", "ENL", "QAE", 4, "FLEX", 120_000, 200_000),
                new Dept("FIN", "Finance", null, "CMB", "FIM", "ACC", 4, "DAY", 110_000, 180_000),
                new Dept("HR", "Human Resources", null, "CMB", "HRM", "HRE", 3, "DAY", 90_000, 150_000),
                new Dept("SALES", "Sales", null, "KDY", "SAM", "SAE", 5, "DAY", 80_000, 160_000)));
        company("BETA", "Beta Logistics (Pvt) Ltd", "B", List.of(
                new Dept("WH", "Warehouse", null, "CMB", "OPM", "MOP", 9, "DAY", 55_000, 95_000),
                new Dept("ADM", "Administration", null, "CMB", "HRM", "ACC", 3, "DAY", 90_000, 150_000)));
        StringBuilder table = new StringBuilder();
        credentials.forEach((user, role) -> table.append(String.format("%n  %-16s %s", user, role)));
        log.info("Demo data seeded in {} s. Sign in with password {} as:{}",
                (System.nanoTime() - start) / 1_000_000_000, PASSWORD, table);
    }

    // ------------------------------------------------------------------------------------------------ one company

    private void company(String code, String legalName, String badgePrefix, List<Dept> depts) {
        log.info("Seeding company {}", code);
        String cid = id(post("/companies", Map.of("code", code, "legalName", legalName,
                "address", "No. 42, Galle Road, Colombo 03", "phone", "+94 11 234 5678",
                "email", "info@" + code.toLowerCase() + ".example")));
        ZoneId zone = ZoneId.of(get("/companies/" + cid).get("timeZone").asString());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate prevMonth = today.minusMonths(1).withDayOfMonth(1);

        // Monday–Friday week
        ObjectNode settings = (ObjectNode) get("/companies/" + cid + "/settings");
        settings.set("weeklyOffDays", json.valueToTree(List.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)));
        send(HttpMethod.PUT, "/companies/" + cid + "/settings", settings);

        Map<String, String> ids = new HashMap<>();
        ids.put("CMB", id(post("/companies/" + cid + "/branches", Map.of("code", "CMB", "name", "Colombo Head Office",
                "address", "No. 42, Galle Road, Colombo 03", "phone", "+94 11 234 5678"))));
        ids.put("KDY", id(post("/companies/" + cid + "/branches", Map.of("code", "KDY", "name", "Kandy Branch",
                "address", "Peradeniya Road, Kandy", "phone", "+94 81 222 3344"))));
        int cc = 100;
        for (Dept d : depts) {
            Map<String, Object> body = new HashMap<>(Map.of("code", d.code(), "name", d.name(), "costCenterCode", "CC-" + cc));
            body.put("parentId", d.parent() == null ? null : ids.get(d.parent()));
            body.put("branchId", ids.get(d.branch()));
            ids.put(d.code(), id(post("/companies/" + cid + "/departments", body)));
            cc += 100;
        }
        Map<String, String> designations = Map.of("OPM", "Operations Manager", "MOP", "Machine Operator",
                "ENL", "Engineering Lead", "SE", "Software Engineer", "QAE", "QA Engineer", "FIM", "Finance Manager",
                "ACC", "Accountant", "HRM", "HR Manager", "HRE", "HR Executive", "SAM", "Sales Manager");
        Set<String> used = new HashSet<>();
        depts.forEach(d -> {
            used.add(d.headDesig());
            used.add(d.staffDesig());
        });
        used.add("SAE");
        for (String desig : used) {
            String name = desig.equals("SAE") ? "Sales Executive" : designations.get(desig);
            ids.put("d_" + desig, id(post("/companies/" + cid + "/designations", Map.of("code", desig, "name", name))));
        }
        for (Object[] g : new Object[][] {{"E1", "Entry", 1}, {"S1", "Staff", 2}, {"S2", "Senior Staff", 3}, {"M1", "Manager", 4}}) {
            ids.put("g_" + g[0], id(post("/companies/" + cid + "/grades", Map.of("code", g[0], "name", g[1], "rank", g[2]))));
        }
        for (Object[] c : new Object[][] {{"PERM", "Permanent", true}, {"CONT", "Contract", true}, {"EXEC", "Executive", false}}) {
            ids.put("c_" + c[0], id(post("/companies/" + cid + "/employment-categories",
                    Map.of("code", c[0], "name", c[1], "otEligible", c[2]))));
        }

        Set<LocalDate> holidays = new HashSet<>();
        for (int year = prevMonth.getYear(); year <= today.getYear(); year++) {
            for (Object[] h : new Object[][] {{1, 14, "Tamil Thai Pongal Day", "PUBLIC"}, {2, 4, "Independence Day", "PUBLIC"},
                    {4, 13, "Sinhala & Tamil New Year Eve", "PUBLIC"}, {4, 14, "Sinhala & Tamil New Year Day", "PUBLIC"},
                    {5, 1, "May Day", "PUBLIC"}, {12, 25, "Christmas Day", "PUBLIC"}, {12, 31, "Year-end Bank Holiday", "MERCANTILE"}}) {
                LocalDate date = LocalDate.of(year, (int) h[0], (int) h[1]);
                holidays.add(date);
                post("/companies/" + cid + "/holidays", Map.of("date", date.toString(), "name", h[2], "type", h[3],
                        "allBranches", true, "branchIds", List.of()));
            }
        }
        // a company holiday inside the payroll month, on a weekday
        LocalDate foundation = prevMonth.withDayOfMonth(18);
        while (foundation.getDayOfWeek().getValue() > 5) {
            foundation = foundation.plusDays(1);
        }
        if (holidays.add(foundation)) {
            post("/companies/" + cid + "/holidays", Map.of("date", foundation.toString(), "name", "Company Foundation Day",
                    "type", "COMPANY", "allBranches", true, "branchIds", List.of()));
        }

        String ot = id(post("/companies/" + cid + "/ot-policies", Map.of("name", "Standard OT", "minOtMinutes", 30,
                "roundingMinutes", 15, "requiresApproval", true)));
        post("/companies/" + cid + "/shifts", fields("code", "DAY", "name", "Day shift", "type", "FIXED", "color", "#228BE6",
                "s1Start", "08:30", "s1End", "17:30", "lateGraceMinutes", 10, "breakMode", "FIXED", "breakMinutes", 60,
                "breakThresholdMinutes", 300, "otPolicyId", ot));
        post("/companies/" + cid + "/shifts", fields("code", "NIGHT", "name", "Night shift", "type", "NIGHT", "color", "#7048E8",
                "s1Start", "20:00", "s1End", "06:00", "lateGraceMinutes", 5, "breakMode", "FIXED", "breakMinutes", 60,
                "breakThresholdMinutes", 300, "otPolicyId", ot));
        post("/companies/" + cid + "/shifts", fields("code", "FLEX", "name", "Flexible office", "type", "FLEXIBLE",
                "color", "#12B886", "flexEarliestStart", "07:00", "flexLatestEnd", "20:00", "requiredMinutes", 480,
                "breakMode", "FIXED", "breakMinutes", 60, "breakThresholdMinutes", 300));
        for (JsonNode s : get("/companies/" + cid + "/shifts")) {
            ids.put("s_" + s.get("code").asString(), s.get("id").asString());
        }
        String allowance = null;
        for (JsonNode c : get("/companies/" + cid + "/payroll-components")) {
            if (c.get("code").asString().equals("ALLOWANCE")) {
                allowance = c.get("id").asString();
            }
        }

        // ---- employees (heads first, so staff can report to them) and their salaries
        List<Person> people = new ArrayList<>();
        Map<String, Person> heads = new HashMap<>();
        Set<String> names = new HashSet<>();
        int[] badge = {100};
        for (Dept d : depts) {
            Person parentHead = d.parent() == null ? null : heads.get(d.parent());
            int headBasic = 250_000 + rnd.nextInt(18) * 10_000;
            Person head = employee(cid, ids, d, d.branch(), d.headDesig(), "M1", "EXEC", d.shift(), parentHead,
                    badgePrefix, badge, names, LocalDate.of(2016 + rnd.nextInt(5), 1 + rnd.nextInt(12), 1 + rnd.nextInt(28)));
            salary(head, headBasic, allowance, 20_000 + rnd.nextInt(5) * 5_000);
            heads.put(d.code(), head);
            for (int i = 0; i < d.staff(); i++) {
                // every third operator at the Kandy factory works nights
                boolean night = d.staffDesig().equals("MOP") && i % 3 == 2;
                String shift = night ? "NIGHT" : d.shift();
                String grade = new String[] {"E1", "S1", "S1", "S2"}[rnd.nextInt(4)];
                String category = rnd.nextInt(5) == 0 ? "CONT" : "PERM";
                Person p = employee(cid, ids, d, night ? "KDY" : d.branch(), d.staffDesig(), grade, category, shift,
                        head, badgePrefix, badge, names, LocalDate.of(2019 + rnd.nextInt(6), 1 + rnd.nextInt(12), 1 + rnd.nextInt(28)));
                int basic = d.basicFrom() + rnd.nextInt((d.basicTo() - d.basicFrom()) / 1000 + 1) * 1000;
                salary(p, basic, allowance, rnd.nextInt(3) == 0 ? 5_000 + rnd.nextInt(4) * 2_500 : 0);
                people.add(p);
            }
        }

        // ---- users: every department head approves their team; HR, payroll and two self-service employees
        String lc = code.toLowerCase();
        for (Dept d : depts) {
            Person head = heads.get(d.code());
            boolean hr = d.headDesig().equals("HRM");
            user(lc + "." + d.code().toLowerCase(), head.id(), hr ? "HR_MANAGER" : "DEPARTMENT_MANAGER",
                    hr ? "COMPANY" : "DEPARTMENT", hr ? cid : ids.get(d.code()));
        }
        people.stream().filter(p -> p.deptCode().equals("FIN") || p.deptCode().equals("ADM")).findFirst()
                .ifPresent(p -> user(lc + ".payroll", p.id(), "PAYROLL_OFFICER", "COMPANY", cid));
        user(lc + ".employee", people.get(0).id(), "EMPLOYEE", "OWN", null);
        user(lc + ".employee2", people.get(people.size() - 1).id(), "EMPLOYEE", "OWN", null);

        // ---- leave plan (decided before the punches so nobody punches while on leave)
        Map<String, String> leaveTypes = new HashMap<>();
        for (JsonNode lt : get("/companies/" + cid + "/leave-types")) {
            String name = lt.get("name").asString().toLowerCase();
            for (String kind : List.of("annual", "casual", "medical")) {
                if (name.contains(kind)) {
                    leaveTypes.putIfAbsent(kind, lt.get("id").asString());
                }
            }
        }
        record Leave(Person who, String type, LocalDate from, LocalDate to, String reason) {
        }
        List<Leave> pastLeave = new ArrayList<>();
        Set<String> onLeave = new HashSet<>();
        String[] reasons = {"Family function", "Personal matter", "Fever", "Wedding", "Family trip", "Medical appointment"};
        for (Person p : people) {
            if (rnd.nextInt(3) != 0) {
                continue;
            }
            LocalDate from = workday(prevMonth.plusDays(rnd.nextInt(25)), holidays);
            LocalDate to = rnd.nextInt(4) == 0 ? workday(from.plusDays(1), holidays) : from;
            String kind = new String[] {"annual", "casual", "medical"}[rnd.nextInt(3)];
            if (!leaveTypes.containsKey(kind) || !to.isBefore(today)) {
                continue;
            }
            pastLeave.add(new Leave(p, leaveTypes.get(kind), from, to, reasons[rnd.nextInt(reasons.length)]));
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                onLeave.add(p.id() + d);
            }
        }

        // ---- ~6 weeks of punches from a "device" (late arrivals, overtime, absences, a missed punch-out)
        String apiKey = post("/companies/" + cid + "/attendance-sources", Map.of("name", "Main gate (demo)"))
                .get("apiKey").asString();
        LocalDateTime now = LocalDateTime.now(clock.withZone(zone));
        LocalDate missedPunch = workday(today.minusDays(1), holidays);
        while (!missedPunch.isBefore(today)) {
            missedPunch = missedPunch.minusDays(1);
        }
        List<Map<String, Object>> events = new ArrayList<>();
        List<Person> everyone = new ArrayList<>(heads.values());
        everyone.addAll(people);
        for (LocalDate day = prevMonth; !day.isAfter(today); day = day.plusDays(1)) {
            if (day.getDayOfWeek().getValue() > 5 || holidays.contains(day)) {
                continue;
            }
            for (Person p : everyone) {
                if (onLeave.contains(p.id() + day) || p.join().isAfter(day) || rnd.nextInt(40) == 0) {
                    continue; // on leave or absent
                }
                LocalDateTime in;
                LocalDateTime out;
                if (p.shift().equals("NIGHT")) {
                    in = day.atTime(19, 40).plusMinutes(rnd.nextInt(30));
                    out = day.plusDays(1).atTime(6, 0).plusMinutes(rnd.nextInt(70));
                } else {
                    boolean late = rnd.nextInt(8) == 0;
                    in = day.atTime(8, 30).plusMinutes(late ? 12 + rnd.nextInt(35) : -rnd.nextInt(25));
                    out = day.atTime(17, 30).plusMinutes(rnd.nextInt(5) < 2 ? 45 + rnd.nextInt(120) : rnd.nextInt(20));
                }
                if (in.isAfter(now)) {
                    continue;
                }
                events.add(punch(p, in, "IN"));
                boolean missed = day.equals(missedPunch) && p == people.get(1);
                if (!out.isAfter(now) && !missed) {
                    events.add(punch(p, out, "OUT"));
                }
            }
        }
        for (int from = 0; from < events.size(); from += 2000) {
            ingest(apiKey, events.subList(from, Math.min(events.size(), from + 2000)));
        }
        // two punches from a badge nobody has yet (shows up under unmatched events)
        LocalDate y = today.minusDays(1);
        ingest(apiKey, List.of(
                Map.of("externalId", "X999-in-" + y, "deviceId", "GATE-02", "employeeIdentifier", badgePrefix + "999",
                        "timestamp", y.atTime(8, 12).toString(), "direction", "IN"),
                Map.of("externalId", "X999-out-" + y, "deviceId", "GATE-02", "employeeIdentifier", badgePrefix + "999",
                        "timestamp", y.atTime(17, 41).toString(), "direction", "OUT")));

        // ---- leave: past requests approved by the managers; a few upcoming ones left pending
        for (Leave l : pastLeave) {
            applyLeave(l.who(), l.type(), l.from(), l.to(), l.reason());
        }
        for (Person head : heads.values()) {
            AppUser approver = users.findByEmployeeId(UUID.fromString(head.id())).orElse(null);
            if (approver == null) {
                continue;
            }
            for (JsonNode item : as(approver, () -> get("/approval/inbox"))) {
                as(approver, () -> post("/approval/transactions/" + item.get("id").asString() + "/approve",
                        Map.of("comment", "Approved")));
            }
        }
        for (int i = 0; i < 4 && leaveTypes.containsKey("casual"); i++) {
            Person p = people.get(rnd.nextInt(people.size()));
            LocalDate from = workday(today.plusDays(5 + rnd.nextInt(20)), holidays);
            applyLeave(p, leaveTypes.get("casual"), from, from, reasons[rnd.nextInt(reasons.length)]);
        }

        // ---- overtime of last month approved; this month's left pending for the OT approval screen
        LocalDate prevEnd = prevMonth.with(TemporalAdjusters.lastDayOfMonth());
        for (int guard = 0; guard < 20; guard++) {
            JsonNode page = get("/attendance/exceptions?companyId=" + cid + "&from=" + prevMonth + "&to=" + prevEnd
                    + "&type=OT_PENDING&size=200");
            List<String> dailyIds = new ArrayList<>();
            page.get("content").forEach(x -> dailyIds.add(x.get("id").asString()));
            if (dailyIds.isEmpty()) {
                break;
            }
            post("/attendance/ot/approve", Map.of("dailyIds", dailyIds, "comment", "Approved (demo data)"));
        }

        // ---- next week's roster for the first department, published (night workers on nights, weekend off)
        Dept first = depts.get(0);
        LocalDate nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        String roster = id(post("/rosters", Map.of("companyId", cid, "departmentId", ids.get(first.code()),
                "name", first.name() + " — week of " + nextMonday, "periodType", "WEEKLY", "startDate", nextMonday.toString())));
        List<Person> team = everyone.stream().filter(p -> p.deptCode().equals(first.code())).toList();
        for (String shift : List.of("DAY", "NIGHT")) {
            List<String> members = team.stream().filter(p -> p.shift().equals(shift)).map(Person::id).toList();
            if (!members.isEmpty()) {
                bulkAssign(roster, members, nextMonday, nextMonday.plusDays(4), ids.get("s_" + shift), false);
            }
        }
        bulkAssign(roster, team.stream().map(Person::id).toList(), nextMonday.plusDays(5), nextMonday.plusDays(6), null, true);
        post("/rosters/" + roster + "/publish", null);

        // ---- last month's payroll, processed and ready for review / approval
        String period = id(post("/payroll/periods", Map.of("companyId", cid, "year", prevMonth.getYear(),
                "month", prevMonth.getMonthValue())));
        post("/payroll/periods/" + period + "/process", null);
        log.info("Company {}: {} employees, {} punches, {} leave requests", code, everyone.size(), events.size(),
                pastLeave.size());
    }

    private Person employee(String cid, Map<String, String> ids, Dept d, String branch, String desig, String grade,
            String category, String shift, Person manager, String badgePrefix, int[] badge, Set<String> names, LocalDate join) {
        boolean male = rnd.nextBoolean();
        String first;
        String last;
        do {
            first = male ? MALE[rnd.nextInt(MALE.length)] : FEMALE[rnd.nextInt(FEMALE.length)];
            last = LAST[rnd.nextInt(LAST.length)];
        } while (!names.add(first + " " + last));
        String b = badgePrefix + ++badge[0];
        Map<String, Object> body = new HashMap<>();
        body.put("companyId", cid);
        body.put("branchId", ids.get(branch));
        body.put("departmentId", ids.get(d.code()));
        body.put("designationId", ids.get("d_" + desig));
        body.put("gradeId", ids.get("g_" + grade));
        body.put("categoryId", ids.get("c_" + category));
        body.put("title", male ? "Mr" : "Ms");
        body.put("firstName", first);
        body.put("lastName", last);
        body.put("gender", male ? "MALE" : "FEMALE");
        body.put("dateOfBirth", LocalDate.of(1972 + rnd.nextInt(30), 1 + rnd.nextInt(12), 1 + rnd.nextInt(28)).toString());
        body.put("nationality", "Sri Lankan");
        body.put("joinDate", join.toString());
        body.put("reportingManagerId", manager == null ? null : manager.id());
        body.put("defaultShiftId", ids.get("s_" + shift));
        body.put("personalEmail", (first + "." + last).toLowerCase() + "@example.com");
        body.put("mobile", String.format("+94 7%d %03d %04d", rnd.nextInt(9), rnd.nextInt(1000), rnd.nextInt(10000)));
        body.put("badgeId", b);
        body.put("customFields", Map.of());
        return new Person(id(post("/employees", body)), b, shift, manager == null ? null : manager.id(), d.code(), join);
    }

    private void salary(Person p, int basic, String allowance, int allowanceAmount) {
        List<Map<String, Object>> lines = new ArrayList<>();
        if (allowance != null && allowanceAmount > 0) {
            Map<String, Object> line = new HashMap<>(Map.of("componentId", allowance, "amount", allowanceAmount));
            line.put("excluded", false);
            lines.add(line);
        }
        post("/employees/" + p.id() + "/salary-structures", Map.of("effectiveFrom", p.join().toString(),
                "basicSalary", basic, "lines", lines));
    }

    private void user(String username, String employeeId, String role, String scope, String scopeId) {
        JsonNode emp = get("/employees/" + employeeId);
        String name = emp.get("firstName").asString() + " " + emp.get("lastName").asString();
        Map<String, Object> body = new HashMap<>(Map.of("username", username, "displayName", name,
                "email", username + "@example.com", "password", PASSWORD, "active", true, "roles", List.of(role),
                "employeeId", employeeId, "scopeType", scope));
        if (scope.equals("COMPANY")) {
            body.put("scopeCompanyIds", List.of(scopeId));
        } else if (scope.equals("DEPARTMENT")) {
            body.put("scopeDepartmentIds", List.of(scopeId));
        }
        post("/admin/users", body);
        // demo accounts skip the forced first-login password change
        tx.executeWithoutResult(s -> users.findByUsernameIgnoreCase(username).ifPresent(u -> {
            u.setMustChangePassword(false);
            principals.evict(u.getId());
        }));
        credentials.put(username, role + " — " + name);
    }

    private void applyLeave(Person p, String type, LocalDate from, LocalDate to, String reason) {
        Map<String, Object> body = new HashMap<>(Map.of("employeeId", p.id(), "leaveTypeId", type,
                "fromDate", from.toString(), "toDate", to.toString(), "reason", reason));
        try {
            post("/leave/requests", body);
        } catch (IllegalStateException e) {
            // leave rules (entitlement, notice, service period) may refuse some random picks; that is fine for demo data
            log.debug("Skipped a demo leave request: {}", e.getMessage());
        }
    }

    private void bulkAssign(String roster, List<String> employees, LocalDate from, LocalDate to, String shift, boolean off) {
        Map<String, Object> body = new HashMap<>(Map.of("employeeIds", employees, "from", from.toString(),
                "to", to.toString(), "weekdays", List.of(), "off", off));
        body.put("shiftId", shift);
        post("/rosters/" + roster + "/bulk-assign", body);
    }

    private static Map<String, Object> punch(Person p, LocalDateTime at, String direction) {
        return Map.of("externalId", p.badge() + "-" + at + "-" + direction, "deviceId", "GATE-01",
                "employeeIdentifier", p.badge(), "timestamp", at.toString(), "direction", direction);
    }

    /** {@code day}, or the next weekday after it that is not a holiday. */
    private static LocalDate workday(LocalDate day, Set<LocalDate> holidays) {
        while (day.getDayOfWeek().getValue() > 5 || holidays.contains(day)) {
            day = day.plusDays(1);
        }
        return day;
    }

    // ------------------------------------------------------------------------------------------------ HTTP

    /** Runs API calls as {@code user} instead of demo.admin. */
    private <T> T as(AppUser user, Supplier<T> call) {
        actor = user;
        try {
            return call.get();
        } finally {
            actor = admin;
        }
    }

    private JsonNode get(String path) {
        return send(HttpMethod.GET, path, null);
    }

    private JsonNode post(String path, Object body) {
        return send(HttpMethod.POST, path, body);
    }

    private void ingest(String apiKey, List<Map<String, Object>> events) {
        http.post().uri("/integrations/attendance/events").header("X-Api-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON).body(json.writeValueAsString(Map.of("events", events)))
                .retrieve().toBodilessEntity();
    }

    private JsonNode send(HttpMethod method, String path, Object body) {
        var request = http.method(method).uri(path).header("Authorization", "Bearer " + tokens.issueAccessToken(actor));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(json.writeValueAsString(body));
        }
        try {
            String response = request.retrieve().body(String.class);
            return response == null || response.isBlank() ? json.nullNode() : json.readTree(response);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException(method + " " + path + " → " + e.getStatusCode() + ": "
                    + e.getResponseBodyAsString(), e);
        }
    }

    /** A request body from key/value pairs (unlike {@code Map.of}: any length). */
    private static Map<String, Object> fields(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static String id(JsonNode node) {
        return node.get("id").asString();
    }
}
