package dev.rahmatullin.engine;

import dev.rahmatullin.domain.WarehouseState;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class MivarGraphEngine {

    private final Map<String, List<MivarRule>> ruleGraph = new ConcurrentHashMap<>();

    public void registerRule(MivarRule rule) {
        Set<String> triggers = rule.getTriggers();

        if (triggers != null && !triggers.isEmpty()) {
            for (String trigger : triggers) {
                ruleGraph.computeIfAbsent(trigger, k -> new CopyOnWriteArrayList<>()).add(rule);
            }
        } else {
            log.warn("ATTENTION: RULE {} has no triggers and will not be registered.", rule.getClass().getSimpleName());
        }
    }


    public void fireEvents(WarehouseState state, Set<String> initialEvents) {
        if (initialEvents == null || initialEvents.isEmpty()) {
            log.warn("No initial events provided. Engine will not start.");
            return;
        }
        Queue<String> eventsQueue = new ArrayDeque<>(initialEvents);

        int iterationCounter = 0;
        final int MAX_ITERATIONS = 10_000;

        while (!eventsQueue.isEmpty()) {
            String currentEvent = eventsQueue.poll();

            if (iterationCounter++ > MAX_ITERATIONS) {
                throw new IllegalStateException("CRITICAL ERROR: CIRCULAR LOOP DETECTED!");
            }

            List<MivarRule> dependentRules = ruleGraph.getOrDefault(currentEvent, Collections.emptyList());

            for (MivarRule rule : dependentRules) {
                if (rule.isApplicable(state)) {
                    Set<String> newEvents = rule.execute(state);
                    log.info("RULE: {} EXECUTED FOR EVENT: {}", rule.getClass().getSimpleName(), currentEvent);
                    if (newEvents != null && !newEvents.isEmpty()) {
                        eventsQueue.addAll(newEvents);
                    }
                }
            }
        }
    }

}
