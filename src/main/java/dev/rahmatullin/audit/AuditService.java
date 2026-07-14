package dev.rahmatullin.audit;

import java.util.List;

public interface AuditService {
    void record(AuditEntry entry);
    List<AuditEntry> queryByTrace(String traceId);
}
