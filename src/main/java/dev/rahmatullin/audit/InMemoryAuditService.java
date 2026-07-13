package dev.rahmatullin.audit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public class InMemoryAuditService implements AuditService {
    private final ConcurrentLinkedQueue<AuditEntry> store = new ConcurrentLinkedQueue<>();

    @Override
    public void record(AuditEntry entry) {
        store.add(entry);
    }

    @Override
    public List<AuditEntry> queryByTrace(String traceId) {
        if (traceId == null) return all();
        return store.stream().filter(e -> traceId.equals(e.getTraceId())).collect(Collectors.toList());
    }

    // Non-interface helper: return all entries
    public List<AuditEntry> all() {
        List<AuditEntry> out = new ArrayList<>();
        store.forEach(out::add);
        return out;
    }
}
