package com.is2c.hrm.approval.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.is2c.hrm.approval.api.ApprovalHandler;

/** Registered approval handlers by transaction type; exactly one handler per type. */
@Component
public class HandlerRegistry {

    private final Map<String, ApprovalHandler> handlers = new HashMap<>();

    public HandlerRegistry(List<ApprovalHandler> list) {
        for (ApprovalHandler h : list) {
            if (handlers.put(h.type(), h) != null) {
                throw new IllegalStateException("Duplicate approval handler for type " + h.type());
            }
        }
    }

    public ApprovalHandler get(String type) {
        ApprovalHandler h = handlers.get(type);
        if (h == null) {
            throw new IllegalStateException("No approval handler for type " + type);
        }
        return h;
    }

    public Set<String> types() {
        return Set.copyOf(handlers.keySet());
    }
}
