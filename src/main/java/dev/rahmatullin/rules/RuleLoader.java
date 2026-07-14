package dev.rahmatullin.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads rule JSON files from a directory and supports hot-reload via WatchService.
 */
public class RuleLoader {
    private final Path rulesDir;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);
    private final RuleRegistrar registrar;
    private final Map<Path, String> fileToRuleId = new ConcurrentHashMap<>();

    public RuleLoader(Path rulesDir, RuleRegistrar registrar) {
        this.rulesDir = rulesDir;
        this.registrar = registrar;
    }

    public void loadAll() throws IOException {
        if (!Files.exists(rulesDir) || !Files.isDirectory(rulesDir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rulesDir, "*.json")) {
            for (Path p : stream) {
                try {
                    loadAndRegister(p);
                } catch (Exception e) {
                    System.err.println("Ошибка загрузки правила: " + p);
                    continue;
                }
            }
        }
    }

    private void loadAndRegister(Path p) throws IOException {
        RuleDefinition def = mapper.readValue(p.toFile(), RuleDefinition.class);
        if (def.getId() == null || def.getId().isBlank()
                || def.getTriggers() == null || def.getTriggers().isEmpty()
                || def.getActions() == null || def.getActions().isEmpty()) {
            throw new IllegalArgumentException("Missing required fields");
        }
        registrar.updateDefinition(def);
        fileToRuleId.put(p.toAbsolutePath().normalize(), def.getId());
        System.out.println("Rule loaded: " + def.getId());
    }

    public void startWatcher() {
        Thread t = new Thread(() -> {
            try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
                rulesDir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                while (!Thread.currentThread().isInterrupted()) {
                    WatchKey key = watcher.take();
                    for (WatchEvent<?> ev : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = ev.kind();
                        Path changed = rulesDir.resolve((Path) ev.context());
                        if (!changed.toString().endsWith(".json")) continue;
                        try {
                            if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                                String id = fileToRuleId.remove(changed.toAbsolutePath().normalize());
                                if (id == null) {
                                    String fileName = changed.getFileName().toString();
                                    id = fileName.substring(0, fileName.lastIndexOf('.'));
                                }
                                registrar.deactivate(id);
                                System.out.println("Rule file deleted, deactivated: " + id);
                            } else {
                                loadAndRegister(changed);
                            }
                        } catch (Exception ex) {
                            System.err.println("Ошибка загрузки правила: " + changed);
                        }
                    }
                    key.reset();
                }
            } catch (Exception ex) {
                System.err.println("RuleLoader watcher stopped: " + ex.getMessage());
            }
        }, "rule-loader-watcher");
        t.setDaemon(true);
        t.start();
    }
}
