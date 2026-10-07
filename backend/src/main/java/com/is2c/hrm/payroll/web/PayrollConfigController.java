package com.is2c.hrm.payroll.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.is2c.hrm.org.service.OrgDirectory;
import com.is2c.hrm.payroll.api.CalcType;
import com.is2c.hrm.payroll.api.ComponentKind;
import com.is2c.hrm.payroll.api.UnitMetric;
import com.is2c.hrm.payroll.domain.PayrollComponent;
import com.is2c.hrm.payroll.domain.SlabRow;
import com.is2c.hrm.payroll.domain.SlabTable;
import com.is2c.hrm.payroll.service.PayrollConfigService;
import com.is2c.hrm.payroll.service.StatutoryPackRegistry;

@RestController
@RequestMapping("/api/v1")
public class PayrollConfigController {

    private final PayrollConfigService config;
    private final StatutoryPackRegistry packs;
    private final OrgDirectory org;

    public PayrollConfigController(PayrollConfigService config, StatutoryPackRegistry packs, OrgDirectory org) {
        this.config = config;
        this.packs = packs;
        this.org = org;
    }

    public record ComponentDto(UUID id, UUID companyId, String code, String name, ComponentKind kind,
            PayrollComponent.Frequency frequency, PayrollComponent.Assignment assignment, CalcType calcType,
            int sequence, Set<String> bases, String basisRef, BigDecimal ratePercent, UnitMetric unitMetric,
            PayrollComponent.UnitRateMode unitRateMode, BigDecimal unitFixedAmount, BigDecimal divisor,
            BigDecimal multiplier, UUID slabTableId, boolean prorate, boolean showOnPayslip, boolean statutory,
            boolean systemComponent, boolean active, Long version) {
        static ComponentDto of(PayrollComponent c) {
            return new ComponentDto(c.getId(), c.getCompanyId(), c.getCode(), c.getName(), c.getKind(),
                    c.getFrequency(), c.getAssignment(), c.getCalcType(), c.getSequence(), c.baseSet(), c.getBasisRef(),
                    c.getRatePercent(), c.getUnitMetric(), c.getUnitRateMode(), c.getUnitFixedAmount(), c.getDivisor(),
                    c.getMultiplier(), c.getSlabTableId(), c.isProrate(), c.isShowOnPayslip(), c.isStatutory(),
                    c.isSystemComponent(), c.isActive(), c.getVersion());
        }
    }

    public record SlabTableDto(UUID id, UUID companyId, String code, String name, boolean statutory,
            List<SlabRow> rows, Long version) {
        static SlabTableDto of(SlabTable t) {
            return new SlabTableDto(t.getId(), t.getCompanyId(), t.getCode(), t.getName(), t.isStatutory(),
                    List.copyOf(t.getRows()), t.getVersion());
        }
    }

    public record StatutoryDto(String pack, List<ComponentDto> components, List<SlabTableDto> slabTables) {
    }

    public record PackDto(String code, String description) {
    }

    @GetMapping("/payroll/statutory-packs")
    public List<PackDto> packs() {
        return packs.all().stream().map(p -> new PackDto(p.code(), p.description())).toList();
    }

    @GetMapping("/companies/{companyId}/payroll-components")
    @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
    public List<ComponentDto> components(@PathVariable UUID companyId) {
        return config.components(companyId).stream().map(ComponentDto::of).toList();
    }

    @PostMapping("/companies/{companyId}/payroll-components")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PAYROLL:CONFIGURE')")
    public ComponentDto createComponent(@PathVariable UUID companyId, @RequestBody PayrollConfigService.ComponentData body) {
        return ComponentDto.of(config.createComponent(companyId, body));
    }

    @PutMapping("/payroll-components/{id}")
    @PreAuthorize("hasAuthority('PAYROLL:CONFIGURE')")
    public ComponentDto updateComponent(@PathVariable UUID id, @RequestBody PayrollConfigService.ComponentData body) {
        return ComponentDto.of(config.updateComponent(id, body));
    }

    @GetMapping("/companies/{companyId}/slab-tables")
    @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
    public List<SlabTableDto> slabTables(@PathVariable UUID companyId) {
        return config.slabTables(companyId).stream().map(SlabTableDto::of).toList();
    }

    @PostMapping("/companies/{companyId}/slab-tables")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PAYROLL:CONFIGURE')")
    public SlabTableDto createSlabTable(@PathVariable UUID companyId, @RequestBody PayrollConfigService.SlabData body) {
        return SlabTableDto.of(config.createSlabTable(companyId, body));
    }

    @PutMapping("/slab-tables/{id}")
    @PreAuthorize("hasAuthority('PAYROLL:CONFIGURE')")
    public SlabTableDto updateSlabTable(@PathVariable UUID id, @RequestBody PayrollConfigService.SlabData body) {
        return SlabTableDto.of(config.updateSlabTable(id, body));
    }

    @GetMapping("/companies/{companyId}/statutory")
    @PreAuthorize("hasAuthority('PAYROLL:VIEW')")
    public StatutoryDto statutory(@PathVariable UUID companyId) {
        PayrollConfigService.StatutoryView v = config.statutory(companyId, org.company(companyId).getStatutoryPack());
        return new StatutoryDto(v.pack(), v.components().stream().map(ComponentDto::of).toList(),
                v.slabTables().stream().map(SlabTableDto::of).toList());
    }
}
