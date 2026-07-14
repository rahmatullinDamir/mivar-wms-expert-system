package dev.rahmatullin.rules;

import dev.rahmatullin.audit.AuditService;
import dev.rahmatullin.engine.MivarGraphEngine;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper to register rules into the existing MivarGraphEngine.
 * Keeps wrappers by id so definitions can be updated in-place for hot-reload.
 */
public class RuleRegistrar {
    private final MivarGraphEngine engine;
    private final AuditService auditService;
    private final Map<String, AuditedRuleWrapper> registry = new ConcurrentHashMap<>();

    public RuleRegistrar(MivarGraphEngine engine, AuditService auditService) {
        this.engine = engine;
        this.auditService = auditService;
    }

    public synchronized void registerFromDefinition(RuleDefinition def) {
        if (def == null || !def.isActive()) return;
        DataDrivenRule drv = new DataDrivenRule(def);
        AuditedRuleWrapper wrapper = new AuditedRuleWrapper(def.getId(), drv, auditService);
        registry.put(def.getId(), wrapper);

        // Register wrapper with engine for each trigger. Important: to preserve priority order,
        // registration should happen in order of decreasing priority at startup. For dynamic adds we just add.
        engine.registerRule(wrapper);
    }

    public synchronized void updateDefinition(RuleDefinition def) {
        AuditedRuleWrapper wrapper = registry.get(def.getId());
        if (wrapper != null) {
            wrapper.updateDefinition(def);
            wrapper.setActive(def.isActive());
        } else {
            registerFromDefinition(def);
        }
    }

    public synchronized void deactivate(String id) {
        AuditedRuleWrapper wrapper = registry.get(id);
        if (wrapper != null) wrapper.setActive(false);
    }

    public Optional<AuditedRuleWrapper> getWrapper(String id) {
        return Optional.ofNullable(registry.get(id));
    }
}
