package com.is2c.hrm.payroll.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

import com.is2c.hrm.org.api.StatutoryPackCatalog;
import com.is2c.hrm.payroll.api.StatutoryPack;

/** Available statutory packs (FR-081); also tells org which pack codes are valid for companies. */
@Component
public class StatutoryPackRegistry implements StatutoryPackCatalog {

    private final Map<String, StatutoryPack> packs = new TreeMap<>();

    public StatutoryPackRegistry(List<StatutoryPack> list) {
        list.forEach(p -> packs.put(p.code(), p));
    }

    @Override
    public Set<String> codes() {
        return Set.copyOf(packs.keySet());
    }

    public Optional<StatutoryPack> get(String code) {
        return Optional.ofNullable(packs.get(code));
    }

    public List<StatutoryPack> all() {
        return List.copyOf(packs.values());
    }
}
