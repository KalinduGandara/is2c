package com.is2c.biostarsync;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The single settings-and-sync page. */
@Controller
class WebController {

    /** A BioStar user group with its user count, for the mapping table. */
    record Group(String id, String name, int users) {
    }

    /** Choices loaded from both servers; cached until the operator reloads or changes a connection. */
    record Lookups(List<Group> groups, List<HrmClient.Company> companies, List<HrmClient.Option> branches,
            List<HrmClient.Option> departments, List<HrmClient.Option> designations,
            List<HrmClient.Option> categories, List<String> errors) {
    }

    private final SettingsStore store;
    private final SyncService sync;
    private volatile Lookups lookups;

    WebController(SettingsStore store, SyncService sync) {
        this.store = store;
        this.sync = sync;
    }

    @GetMapping("/")
    String page(Model model) {
        Settings s = store.settings();
        Lookups l = lookups;
        if (l == null) {
            l = load(s);
            lookups = l;
        }
        model.addAttribute("s", s);
        model.addAttribute("state", store.state());
        model.addAttribute("running", sync.running());
        model.addAttribute("lookups", l);
        model.addAttribute("missing", s.missingForSync());
        return "index";
    }

    @PostMapping("/biostar")
    String saveBiostar(@RequestParam String url, @RequestParam String loginId,
            @RequestParam(defaultValue = "") String password, @RequestParam(defaultValue = "false") boolean trustAllCerts,
            RedirectAttributes flash) {
        if (!validUrl(url, flash)) {
            return "redirect:/";
        }
        Settings s = store.settings();
        s.biostarUrl = url.trim();
        s.biostarLoginId = loginId.trim();
        if (!password.isEmpty()) {
            s.biostarPassword = password;
        }
        s.biostarTrustAllCerts = trustAllCerts;
        store.save(s);
        lookups = null;
        try {
            BiostarClient bio = sync.biostar(s);
            bio.login(s.biostarLoginId, s.biostarPassword);
            flash.addFlashAttribute("message", "BioStar connection saved and working (" + bio.users().size() + " users).");
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", "BioStar settings saved, but the connection failed: " + e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/hrm")
    String saveHrm(@RequestParam String url, @RequestParam String username,
            @RequestParam(defaultValue = "") String password, @RequestParam(defaultValue = "") String apiKey,
            RedirectAttributes flash) {
        if (!validUrl(url, flash)) {
            return "redirect:/";
        }
        Settings s = store.settings();
        s.hrmUrl = url.trim();
        s.hrmUsername = username.trim();
        if (!password.isEmpty()) {
            s.hrmPassword = password;
        }
        if (!apiKey.isBlank()) {
            s.hrmApiKey = apiKey.trim();
        }
        store.save(s);
        lookups = null;
        try {
            sync.hrm(s).login();
            flash.addFlashAttribute("message", "HRM connection saved and working.");
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", "HRM settings saved, but the connection failed: " + e.getMessage());
        }
        return "redirect:/";
    }

    /** Choosing another company resets everything that belongs to the old one. */
    @PostMapping("/company")
    String saveCompany(@RequestParam String companyId, RedirectAttributes flash) {
        Settings s = store.settings();
        if (!companyId.equals(s.companyId)) {
            s.companyId = companyId;
            s.branchId = "";
            s.designationId = "";
            s.categoryId = "";
            s.groupDepartments = new LinkedHashMap<>();
            store.save(s);
            lookups = null;
        }
        flash.addFlashAttribute("message", "Company selected; now set the employee defaults and group mapping.");
        return "redirect:/";
    }

    @PostMapping("/mapping")
    String saveMapping(@RequestParam Map<String, String> form, RedirectAttributes flash) {
        Settings s = store.settings();
        LocalDate joinDate = date(form.get("joinDate"));
        if (joinDate == null) {
            flash.addFlashAttribute("error", "Enter a valid default join date.");
            return "redirect:/";
        }
        s.branchId = form.getOrDefault("branchId", "");
        s.designationId = form.getOrDefault("designationId", "");
        s.categoryId = form.getOrDefault("categoryId", "");
        s.joinDate = joinDate;
        s.tnaKeyIn = form.getOrDefault("tnaKeyIn", "").trim();
        s.tnaKeyOut = form.getOrDefault("tnaKeyOut", "").trim();
        Map<String, String> groups = new LinkedHashMap<>();
        form.forEach((k, v) -> {
            if (k.startsWith("group.")) {
                groups.put(k.substring("group.".length()), v);
            }
        });
        s.groupDepartments = groups;
        store.save(s);
        flash.addFlashAttribute("message", "Employee defaults and group mapping saved.");
        return "redirect:/";
    }

    @PostMapping("/schedule")
    String saveSchedule(@RequestParam(defaultValue = "false") boolean enabled, @RequestParam int intervalMinutes,
            @RequestParam(defaultValue = "") String startDate, @RequestParam int lookbackHours,
            RedirectAttributes flash) {
        Settings s = store.settings();
        LocalDate start = date(startDate);
        if (intervalMinutes < 1 || lookbackHours < 0) {
            flash.addFlashAttribute("error", "The interval must be at least 1 minute and the look-back 0 hours or more.");
            return "redirect:/";
        }
        if (enabled && start == null && store.state().lastEventTime == null) {
            flash.addFlashAttribute("error", "Enter the date the first automatic sync starts from.");
            return "redirect:/";
        }
        s.scheduleEnabled = enabled;
        s.scheduleIntervalMinutes = intervalMinutes;
        s.scheduleStartDate = start;
        s.scheduleLookbackHours = lookbackHours;
        store.save(s);
        flash.addFlashAttribute("message", enabled
                ? "Automatic sync on, every " + intervalMinutes + " minutes." : "Automatic sync off.");
        return "redirect:/";
    }

    @PostMapping("/schedule/reset")
    String resetWatermark(RedirectAttributes flash) {
        store.resetWatermark();
        flash.addFlashAttribute("message",
                "Position reset: the next automatic sync starts again from the start date (duplicates are skipped).");
        return "redirect:/";
    }

    @PostMapping("/sync")
    String startSync(@RequestParam String from, @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean preview, RedirectAttributes flash) {
        LocalDate f = date(from);
        LocalDate t = date(to);
        if (f == null || t == null || t.isBefore(f)) {
            flash.addFlashAttribute("error", "Enter a valid date range (from on or before to).");
        } else if (!sync.start(new SyncService.Request("MANUAL", preview, f, t))) {
            flash.addFlashAttribute("error", "A sync is already running; wait for it to finish.");
        }
        return "redirect:/";
    }

    @PostMapping("/reload")
    String reload() {
        lookups = null;
        return "redirect:/";
    }

    private Lookups load(Settings s) {
        List<String> errors = new ArrayList<>();
        List<Group> groups = List.of();
        if (s.biostarConfigured()) {
            try {
                BiostarClient bio = sync.biostar(s);
                bio.login(s.biostarLoginId, s.biostarPassword);
                Map<String, Group> byId = new TreeMap<>();
                bio.users().stream().filter(u -> u.groupId() != null).forEach(u -> byId.merge(u.groupId(),
                        new Group(u.groupId(), u.groupName(), 1),
                        (a, b) -> new Group(a.id(), a.name(), a.users() + 1)));
                groups = byId.values().stream().sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList();
            } catch (RuntimeException e) {
                errors.add("BioStar: " + e.getMessage());
            }
        }
        List<HrmClient.Company> companies = List.of();
        List<HrmClient.Option> branches = List.of();
        List<HrmClient.Option> departments = List.of();
        List<HrmClient.Option> designations = List.of();
        List<HrmClient.Option> categories = List.of();
        if (s.hrmConfigured()) {
            try {
                HrmClient hrm = sync.hrm(s);
                hrm.login();
                companies = hrm.companies();
                if (!s.companyId.isBlank()) {
                    branches = hrm.branches(s.companyId);
                    departments = hrm.departments(s.companyId);
                    designations = hrm.designations(s.companyId);
                    categories = hrm.categories(s.companyId);
                }
            } catch (RuntimeException e) {
                errors.add("HRM: " + e.getMessage());
            }
        }
        return new Lookups(groups, companies, branches, departments, designations, categories, errors);
    }

    private static boolean validUrl(String url, RedirectAttributes flash) {
        if (url.trim().matches("(?i)https?://\\S+")) {
            return true;
        }
        flash.addFlashAttribute("error", "The URL must start with http:// or https://");
        return false;
    }

    private static LocalDate date(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
