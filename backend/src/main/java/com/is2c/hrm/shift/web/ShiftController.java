package com.is2c.hrm.shift.web;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.is2c.hrm.shift.api.ShiftDefinition.BreakMode;
import com.is2c.hrm.shift.api.ShiftDefinition.ShiftType;
import com.is2c.hrm.shift.domain.OtPolicy;
import com.is2c.hrm.shift.domain.RotationAssignment;
import com.is2c.hrm.shift.domain.RotationPattern;
import com.is2c.hrm.shift.domain.Shift;
import com.is2c.hrm.shift.service.RotationService;
import com.is2c.hrm.shift.service.ShiftService;

@RestController
@RequestMapping("/api/v1")
public class ShiftController {

    private final ShiftService shifts;
    private final RotationService rotations;

    public ShiftController(ShiftService shifts, RotationService rotations) {
        this.shifts = shifts;
        this.rotations = rotations;
    }

    public record ShiftDto(UUID id, UUID companyId, String code, String name, ShiftType type, String color,
            LocalTime s1Start, LocalTime s1End, LocalTime s2Start, LocalTime s2End, LocalTime flexEarliestStart,
            LocalTime flexLatestEnd, Integer requiredMinutes, int lateGraceMinutes, int earlyGraceMinutes,
            BreakMode breakMode, int breakMinutes, int breakThresholdMinutes, Integer halfDayThresholdMinutes,
            UUID otPolicyId, Integer windowBeforeMinutes, Integer windowAfterMinutes, boolean active,
            boolean crossesMidnight, int scheduledMinutes, Long version) {
        static ShiftDto of(Shift s) {
            var def = s.toDefinition(null);
            boolean crosses = !def.isFlexible() && def.sessions().stream().anyMatch(x -> x.crossesMidnight());
            return new ShiftDto(s.getId(), s.getCompanyId(), s.getCode(), s.getName(), s.getType(), s.getColor(),
                    s.getS1Start(), s.getS1End(), s.getS2Start(), s.getS2End(), s.getFlexEarliestStart(),
                    s.getFlexLatestEnd(), s.getRequiredMinutes(), s.getLateGraceMinutes(), s.getEarlyGraceMinutes(),
                    s.getBreakMode(), s.getBreakMinutes(), s.getBreakThresholdMinutes(), s.getHalfDayThresholdMinutes(),
                    s.getOtPolicyId(), s.getWindowBeforeMinutes(), s.getWindowAfterMinutes(), s.isActive(), crosses,
                    def.scheduledMinutes(), s.getVersion());
        }
    }

    public record OtPolicyDto(UUID id, UUID companyId, String name, int minOtMinutes, int roundingMinutes,
            boolean allowPreShiftOt, Integer maxOtMinutes, boolean requiresApproval, Long version) {
        static OtPolicyDto of(OtPolicy p) {
            return new OtPolicyDto(p.getId(), p.getCompanyId(), p.getName(), p.getMinOtMinutes(),
                    p.getRoundingMinutes(), p.isAllowPreShiftOt(), p.getMaxOtMinutes(), p.isRequiresApproval(),
                    p.getVersion());
        }
    }

    public record PatternDto(UUID id, UUID companyId, String name, List<UUID> days, Long version) {
        static PatternDto of(RotationPattern p) {
            // null entries are OFF days, so List.copyOf (null-hostile) cannot be used
            return new PatternDto(p.getId(), p.getCompanyId(), p.getName(), new java.util.ArrayList<>(p.getDays()),
                    p.getVersion());
        }
    }

    public record AssignmentDto(UUID id, UUID employeeId, UUID patternId, LocalDate startDate, LocalDate endDate) {
        static AssignmentDto of(RotationAssignment a) {
            return new AssignmentDto(a.getId(), a.getEmployeeId(), a.getPatternId(), a.getStartDate(), a.getEndDate());
        }
    }

    // Shifts (FR-030)

    @GetMapping("/companies/{companyId}/shifts")
    @PreAuthorize("hasAnyAuthority('SHIFT:VIEW', 'ROSTER:VIEW', 'EMPLOYEE:VIEW')")
    public List<ShiftDto> list(@PathVariable UUID companyId) {
        return shifts.list(companyId).stream().map(ShiftDto::of).toList();
    }

    @GetMapping("/shifts/{id}")
    @PreAuthorize("hasAnyAuthority('SHIFT:VIEW', 'ROSTER:VIEW')")
    public ShiftDto get(@PathVariable UUID id) {
        return ShiftDto.of(shifts.get(id));
    }

    @PostMapping("/companies/{companyId}/shifts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SHIFT:ADD')")
    public ShiftDto create(@PathVariable UUID companyId, @RequestBody ShiftService.ShiftData body) {
        return ShiftDto.of(shifts.create(companyId, body));
    }

    @PutMapping("/shifts/{id}")
    @PreAuthorize("hasAuthority('SHIFT:EDIT')")
    public ShiftDto update(@PathVariable UUID id, @RequestBody ShiftService.ShiftData body) {
        return ShiftDto.of(shifts.update(id, body));
    }

    @PostMapping("/shifts/{id}/deactivate")
    @PreAuthorize("hasAuthority('SHIFT:EDIT')")
    public ShiftDto deactivate(@PathVariable UUID id) {
        return ShiftDto.of(shifts.setActive(id, false));
    }

    @PostMapping("/shifts/{id}/activate")
    @PreAuthorize("hasAuthority('SHIFT:EDIT')")
    public ShiftDto activate(@PathVariable UUID id) {
        return ShiftDto.of(shifts.setActive(id, true));
    }

    // OT policies (FR-032)

    @GetMapping("/companies/{companyId}/ot-policies")
    @PreAuthorize("hasAnyAuthority('SHIFT:VIEW', 'ROSTER:VIEW')")
    public List<OtPolicyDto> policies(@PathVariable UUID companyId) {
        return shifts.policies(companyId).stream().map(OtPolicyDto::of).toList();
    }

    @PostMapping("/companies/{companyId}/ot-policies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SHIFT:CONFIGURE')")
    public OtPolicyDto createPolicy(@PathVariable UUID companyId, @RequestBody ShiftService.OtPolicyData body) {
        return OtPolicyDto.of(shifts.createPolicy(companyId, body));
    }

    @PutMapping("/ot-policies/{id}")
    @PreAuthorize("hasAuthority('SHIFT:CONFIGURE')")
    public OtPolicyDto updatePolicy(@PathVariable UUID id, @RequestBody ShiftService.OtPolicyData body) {
        return OtPolicyDto.of(shifts.updatePolicy(id, body));
    }

    @DeleteMapping("/ot-policies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SHIFT:CONFIGURE')")
    public void deletePolicy(@PathVariable UUID id) {
        shifts.deletePolicy(id);
    }

    // Rotations (FR-031)

    @GetMapping("/companies/{companyId}/rotation-patterns")
    @PreAuthorize("hasAnyAuthority('SHIFT:VIEW', 'ROSTER:VIEW')")
    public List<PatternDto> patterns(@PathVariable UUID companyId) {
        return rotations.list(companyId).stream().map(PatternDto::of).toList();
    }

    @PostMapping("/companies/{companyId}/rotation-patterns")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SHIFT:ADD')")
    public PatternDto createPattern(@PathVariable UUID companyId, @RequestBody RotationService.PatternData body) {
        return PatternDto.of(rotations.create(companyId, body));
    }

    @PutMapping("/rotation-patterns/{id}")
    @PreAuthorize("hasAuthority('SHIFT:EDIT')")
    public PatternDto updatePattern(@PathVariable UUID id, @RequestBody RotationService.PatternData body) {
        return PatternDto.of(rotations.update(id, body));
    }

    @DeleteMapping("/rotation-patterns/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SHIFT:DELETE')")
    public void deletePattern(@PathVariable UUID id) {
        rotations.delete(id);
    }

    @PostMapping("/rotation-patterns/{id}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROSTER:EDIT')")
    public List<AssignmentDto> assign(@PathVariable UUID id, @RequestBody RotationService.AssignmentData body) {
        return rotations.assign(id, body).stream().map(AssignmentDto::of).toList();
    }

    @GetMapping("/employees/{employeeId}/rotation-assignments")
    @PreAuthorize("hasAnyAuthority('ROSTER:VIEW', 'SHIFT:VIEW')")
    public List<AssignmentDto> assignments(@PathVariable UUID employeeId) {
        return rotations.assignmentsOf(employeeId).stream().map(AssignmentDto::of).toList();
    }

    @DeleteMapping("/rotation-assignments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ROSTER:EDIT')")
    public void unassign(@PathVariable UUID id) {
        rotations.unassign(id);
    }
}
