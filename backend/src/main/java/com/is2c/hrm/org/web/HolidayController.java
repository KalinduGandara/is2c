package com.is2c.hrm.org.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.is2c.hrm.org.domain.Holiday;
import com.is2c.hrm.org.service.HolidayService;

@RestController
@RequestMapping("/api/v1")
public class HolidayController {

    private final HolidayService holidays;

    public HolidayController(HolidayService holidays) {
        this.holidays = holidays;
    }

    public record HolidayDto(UUID id, UUID companyId, LocalDate date, String name, Holiday.Type type,
            boolean allBranches, Set<UUID> branchIds, Long version) {
        static HolidayDto of(Holiday h) {
            return new HolidayDto(h.getId(), h.getCompanyId(), h.getDate(), h.getName(), h.getType(),
                    h.isAllBranches(), Set.copyOf(h.getBranchIds()), h.getVersion());
        }
    }

    public record CopyRequest(int fromYear, int toYear) {
    }

    /** Visible to every authenticated user of the company (FR-025); scope check in the service. */
    @GetMapping("/companies/{companyId}/holidays")
    public List<HolidayDto> list(@PathVariable UUID companyId, @RequestParam int year) {
        return holidays.list(companyId, year).stream().map(HolidayDto::of).toList();
    }

    @PostMapping("/companies/{companyId}/holidays")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ORG:CONFIGURE')")
    public HolidayDto create(@PathVariable UUID companyId, @RequestBody HolidayService.HolidayData body) {
        return HolidayDto.of(holidays.create(companyId, body));
    }

    @PutMapping("/holidays/{id}")
    @PreAuthorize("hasAuthority('ORG:CONFIGURE')")
    public HolidayDto update(@PathVariable UUID id, @RequestBody HolidayService.HolidayData body) {
        return HolidayDto.of(holidays.update(id, body));
    }

    @DeleteMapping("/holidays/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ORG:CONFIGURE')")
    public void delete(@PathVariable UUID id) {
        holidays.delete(id);
    }

    @PostMapping("/companies/{companyId}/holidays/copy")
    @PreAuthorize("hasAuthority('ORG:CONFIGURE')")
    public HolidayService.CopyResult copy(@PathVariable UUID companyId, @RequestBody CopyRequest body) {
        return holidays.copyYear(companyId, body.fromYear(), body.toYear());
    }
}
