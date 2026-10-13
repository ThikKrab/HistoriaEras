package com.historiamc.townyeras.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.historiamc.townyeras.HistoriaEras;
import com.historiamc.townyeras.tech.TechClass;
import com.historiamc.townyeras.tech.TechEra;
import com.historiamc.townyeras.tech.TechNode;
import com.historiamc.townyeras.tech.TechRegistry;
import com.historiamc.townyeras.tech.TechRequirement;
import com.historiamc.townyeras.tech.TechnologyManager;
import com.historiamc.townyeras.tech.WebsitePublicationAdapter;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Authenticated bridge between the dedicated server and the HistoriaMC website editor. */
public final class WebsiteTechnologySync {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final AtomicBoolean PUBLISHING = new AtomicBoolean();
    private static final AtomicBoolean POLLING = new AtomicBoolean();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("townyeras-website.properties");
    private static final Path CACHE_PATH = FMLPaths.CONFIGDIR.get().resolve("townyeras-website-tech-tree.json");
    private static final Path STATE_PATH = FMLPaths.CONFIGDIR.get().resolve("townyeras-website-state.properties");

    private static volatile Settings settings;
    private static volatile boolean initialized;
    private static volatile long ticks;
    private static volatile long lastSequence;
    private static volatile boolean publishSoon;
    private static volatile String bundledRevision = "";

    private WebsiteTechnologySync() {
    }

    public static void tick(MinecraftServer server) {
        if (!initialized) {
            initialize(server);
        }
        Settings active = settings;
        if (active == null || !active.enabled()) {
            return;
        }
        ticks++;
        if (publishSoon || ticks % active.publishTicks() == 0L) {
            publishSoon = false;
            publish(server, active);
        }
        if (ticks % active.pollTicks() == 0L) {
            poll(server, active);
        }
    }

    public static boolean enabled() {
        Settings active = settings;
        return active != null && active.enabled();
    }

    public static void requestPublish() {
        publishSoon = true;
    }

    private static synchronized void initialize(MinecraftServer server) {
        if (initialized) return;
        initialized = true;
        settings = loadSettings();
        bundledRevision = TechnologyManager.revision();
        lastSequence = loadSequence();
        String cachedBaseRevision = loadCachedBaseRevision();
        if (Files.isRegularFile(CACHE_PATH) && bundledRevision.equals(cachedBaseRevision)) {
            try {
                TechRegistry cached = TechRegistry.fromNormalizedJson(Files.readString(CACHE_PATH));
                TechnologyManager.install(cached);
                HistoriaEras.LOGGER.info("Loaded website-managed HistoriaEras graph {} with {} nodes",
                        TechnologyManager.revision().substring(0, 12), cached.nodes().size());
            } catch (Exception exception) {
                HistoriaEras.LOGGER.error("Ignoring invalid cached website technology graph", exception);
            }
        } else if (Files.isRegularFile(CACHE_PATH)) {
            HistoriaEras.LOGGER.warn("Ignoring website technology cache built for an older bundled graph; "
                    + "the updated bundled graph will be published before later website edits are applied");
        }
        if (settings.enabled()) {
            HistoriaEras.LOGGER.info("HistoriaMC website synchronization enabled for source {}", settings.sourceKey());
            publishSoon = true;
            poll(server, settings);
        } else {
            HistoriaEras.LOGGER.info("HistoriaMC website synchronization is disabled; configure {}", CONFIG_PATH);
        }
    }

    private static Settings loadSettings() {
        Properties values = new Properties();
        if (Files.isRegularFile(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                values.load(reader);
            } catch (IOException exception) {
                HistoriaEras.LOGGER.error("Could not read {}", CONFIG_PATH, exception);
            }
        } else {
            values.setProperty("enabled", "false");
            values.setProperty("tree-endpoint", "https://historiamc.com/api/minecraft/tech-tree");
            values.setProperty("publications-endpoint", "https://historiamc.com/api/minecraft/tech-publications");
            values.setProperty("source-server-key", "historia-primary");
            values.setProperty("shared-secret", "");
            values.setProperty("poll-seconds", "60");
            values.setProperty("publish-seconds", "300");
            storeProperties(CONFIG_PATH, values, "HistoriaEras website technology synchronization");
        }

        BridgeSettings bridge = findExistingHistoriaBridge();
        String secret = value(values, "shared-secret", bridge.secret());
        String treeEndpoint = value(values, "tree-endpoint", bridge.endpoint());
        String publicationsEndpoint = value(values, "publications-endpoint",
                treeEndpoint.replaceFirst("/tech-tree/?$", "/tech-publications"));
        String source = value(values, "source-server-key", bridge.sourceKey());
        boolean explicitlyEnabled = Boolean.parseBoolean(values.getProperty("enabled", "false"));
        boolean enabled = !secret.isBlank() && (explicitlyEnabled || bridge.enabled());
        return new Settings(
                enabled,
                treeEndpoint,
                publicationsEndpoint,
                source,
                secret,
                secondsToTicks(values.getProperty("poll-seconds"), 60),
                secondsToTicks(values.getProperty("publish-seconds"), 300)
        );
    }

    /** Reuses the already-deployed HistoriaBridge credentials without copying or logging them. */
    private static BridgeSettings findExistingHistoriaBridge() {
        Path[] candidates = {
                FMLPaths.GAMEDIR.get().resolve("plugins/HistoriaBridge/config.yml"),
                FMLPaths.GAMEDIR.get().resolve("plugins/HistoriaBridge/config.yaml")
        };
        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate)) continue;
            try {
                String endpoint = "";
                String source = "";
                String secret = "";
                boolean enabled = false;
                boolean inTechTree = false;
                for (String raw : Files.readAllLines(candidate, StandardCharsets.UTF_8)) {
                    String trimmed = raw.strip();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                    int indentation = raw.length() - raw.stripLeading().length();
                    int colon = trimmed.indexOf(':');
                    if (colon < 0) continue;
                    String key = trimmed.substring(0, colon).strip();
                    String value = unquote(trimmed.substring(colon + 1).strip());
                    if (indentation == 0) {
                        inTechTree = trimmed.equals("tech-tree:");
                        if (key.equals("shared-secret") || key.equals("secret")) secret = value;
                        if (key.equals("source-server-key")) source = value;
                        continue;
                    }
                    if (!inTechTree) continue;
                    switch (key) {
                        case "endpoint" -> endpoint = value;
                        case "source-server-key", "source" -> source = value;
                        case "shared-secret", "secret" -> secret = value;
                        case "enabled", "publishing-enabled" -> enabled |= Boolean.parseBoolean(value);
                        default -> { }
                    }
                }
                if (!secret.isBlank()) return new BridgeSettings(endpoint, source, secret, enabled);
            } catch (IOException exception) {
                HistoriaEras.LOGGER.warn("Could not read existing HistoriaBridge configuration", exception);
            }
        }
        return BridgeSettings.EMPTY;
    }

    private static void publish(MinecraftServer server, Settings active) {
        if (!PUBLISHING.compareAndSet(false, true)) return;
        JsonObject body = new JsonObject();
        body.addProperty("sourceServerKey", active.sourceKey());
        body.addProperty("generatedAt", Instant.now().toString());
        body.add("tree", websiteTree(server, TechnologyManager.current()));
        send(active, active.treeEndpoint(), "POST", GSON.toJson(body))
                .whenComplete((response, failure) -> {
                    PUBLISHING.set(false);
                    if (failure != null) {
                        HistoriaEras.LOGGER.warn("Could not publish the HistoriaEras graph to historiamc.com: {}",
                                failure.getMessage());
                    } else if (response.statusCode() / 100 != 2) {
                        HistoriaEras.LOGGER.warn("HistoriaMC rejected the HistoriaEras graph with HTTP {}", response.statusCode());
                    }
                });
    }

    private static void poll(MinecraftServer server, Settings active) {
        if (!POLLING.compareAndSet(false, true)) return;
        String uri = active.publicationsEndpoint() + "?source="
                + URLEncoder.encode(active.sourceKey(), StandardCharsets.UTF_8);
        send(active, uri, "GET", null).whenComplete((response, failure) -> {
            POLLING.set(false);
            if (failure != null) {
                HistoriaEras.LOGGER.warn("Could not check HistoriaMC technology publications: {}", failure.getMessage());
                return;
            }
            if (response.statusCode() / 100 != 2) {
                HistoriaEras.LOGGER.warn("HistoriaMC publication check returned HTTP {}", response.statusCode());
                return;
            }
            try {
                JsonObject envelope = GSON.fromJson(response.body(), JsonObject.class);
                JsonElement publicationElement = envelope == null ? null : envelope.get("publication");
                if (publicationElement == null || publicationElement.isJsonNull()) return;
                JsonObject publication = publicationElement.getAsJsonObject();
                server.execute(() -> applyPublication(server, active, publication));
            } catch (Exception exception) {
                HistoriaEras.LOGGER.error("HistoriaMC returned an invalid technology publication", exception);
            }
        });
    }

    private static void applyPublication(MinecraftServer server, Settings active, JsonObject publication) {
        String id = publication.has("id") ? publication.get("id").getAsString() : "";
        long sequence = publication.has("sequence") ? publication.get("sequence").getAsLong() : -1L;
        if (id.isBlank() || sequence < 0L || !publication.has("desired")) {
            HistoriaEras.LOGGER.error("Rejected malformed HistoriaMC technology publication");
            return;
        }
        if (sequence <= lastSequence) {
            acknowledge(active, id, false, "Publication sequence was already applied or is stale.");
            return;
        }
        try {
            TechRegistry next = WebsitePublicationAdapter.apply(
                    TechnologyManager.current(), publication.getAsJsonObject("desired"));
            atomicWrite(CACHE_PATH, next.toNormalizedJson());
            TechnologyManager.install(next);
            lastSequence = sequence;
            saveState(sequence);
            ServerSync.syncAll(server);
            acknowledge(active, id, true, null);
            publishSoon = true;
            HistoriaEras.LOGGER.info("Applied HistoriaMC website technology publication {} (sequence {}, {} nodes)",
                    id, sequence, next.nodes().size());
        } catch (Exception exception) {
            String reason = safeFailure(exception);
            HistoriaEras.LOGGER.error("Rejected HistoriaMC technology publication {}: {}", id, reason);
            acknowledge(active, id, false, reason);
        }
    }

    private static void acknowledge(Settings active, String id, boolean applied, String error) {
        JsonObject receipt = new JsonObject();
        receipt.addProperty("id", id);
        receipt.addProperty("sourceServerKey", active.sourceKey());
        receipt.addProperty("applied", applied);
        if (error != null) receipt.addProperty("error", error);
        send(active, active.publicationsEndpoint(), "POST", GSON.toJson(receipt))
                .whenComplete((response, failure) -> {
                    if (failure != null || response.statusCode() / 100 != 2) {
                        HistoriaEras.LOGGER.warn("Could not acknowledge HistoriaMC technology publication {}", id);
                    }
                });
    }

    private static CompletableFuture<HttpResponse<String>> send(
            Settings active, String uri, String method, String body
    ) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(uri))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + active.secret())
                .header("Accept", "application/json")
                .header("User-Agent", "HistoriaEras-NeoForge/0.4");
        if (body == null) request.GET();
        else request.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return HTTP.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static JsonObject websiteTree(MinecraftServer server, TechRegistry registry) {
        JsonObject tree = new JsonObject();
        tree.addProperty("schemaVersion", 1);
        tree.addProperty("currentEraId", HistoriaErasSavedData.get(server).timeline().eraId());
        JsonArray eras = new JsonArray();
        for (TechEra era : registry.eras().values()) {
            JsonObject value = new JsonObject();
            value.addProperty("id", era.id());
            value.addProperty("name", era.displayName());
            eras.add(value);
        }
        tree.add("eras", eras);
        JsonArray classes = new JsonArray();
        for (TechClass definition : registry.classes().values()) {
            JsonObject value = new JsonObject();
            value.addProperty("id", definition.id());
            value.addProperty("name", definition.displayName());
            value.addProperty("enabled", definition.enabled());
            classes.add(value);
        }
        tree.add("classes", classes);
        JsonArray nodes = new JsonArray();
        Map<String, String> bindingNames = new LinkedHashMap<>();
        JsonArray actions = new JsonArray();
        Map<String, Boolean> uniqueActions = new LinkedHashMap<>();
        for (TechNode node : registry.nodes().values()) {
            JsonObject value = new JsonObject();
            value.addProperty("id", node.id());
            value.addProperty("name", node.displayName());
            value.addProperty("era", node.era());
            value.addProperty("classId", node.classId());
            value.addProperty("icon", node.icon());
            value.addProperty("slot", node.slot());
            JsonObject position = new JsonObject();
            position.addProperty("x", node.graphX());
            position.addProperty("y", node.graphY());
            value.add("position", position);
            value.add("legacyBindings", GSON.toJsonTree(node.legacyBindings()));
            value.add("prerequisites", GSON.toJsonTree(node.prerequisites()));
            value.add("requirements", GSON.toJsonTree(node.requirements()));
            value.add("unlockRecipes", GSON.toJsonTree(node.unlockRecipes()));
            value.add("unlockActions", GSON.toJsonTree(node.unlockActions()));
            value.add("processRecipes", GSON.toJsonTree(node.unlocks().processRecipes()));
            value.add("sharedRoutes", GSON.toJsonTree(node.sharedRoutes()));
            nodes.add(value);
            for (String binding : node.legacyBindings()) bindingNames.putIfAbsent(binding, node.displayName());
            for (TechRequirement requirement : node.requirements()) uniqueActions.put(requirement.action(), true);
        }
        tree.add("nodes", nodes);
        uniqueActions.keySet().forEach(actions::add);
        JsonArray bindings = new JsonArray();
        bindingNames.forEach((id, name) -> {
            JsonObject value = new JsonObject();
            value.addProperty("id", id);
            value.addProperty("name", name);
            bindings.add(value);
        });
        JsonObject editor = new JsonObject();
        editor.addProperty("version", 2);
        editor.add("actions", actions);
        editor.add("bindings", bindings);
        tree.add("editor", editor);
        return tree;
    }

    private static long loadSequence() {
        Properties state = new Properties();
        if (!Files.isRegularFile(STATE_PATH)) return -1L;
        try (Reader reader = Files.newBufferedReader(STATE_PATH, StandardCharsets.UTF_8)) {
            state.load(reader);
            return Long.parseLong(state.getProperty("last-applied-sequence", "-1"));
        } catch (Exception exception) {
            HistoriaEras.LOGGER.warn("Could not read website publication state", exception);
            return -1L;
        }
    }

    private static String loadCachedBaseRevision() {
        Properties state = new Properties();
        if (!Files.isRegularFile(STATE_PATH)) return "";
        try (Reader reader = Files.newBufferedReader(STATE_PATH, StandardCharsets.UTF_8)) {
            state.load(reader);
            return state.getProperty("bundled-revision", "");
        } catch (Exception exception) {
            HistoriaEras.LOGGER.warn("Could not read cached website base revision", exception);
            return "";
        }
    }

    private static void saveState(long sequence) {
        Properties state = new Properties();
        state.setProperty("last-applied-sequence", Long.toString(sequence));
        state.setProperty("bundled-revision", bundledRevision);
        storeProperties(STATE_PATH, state, "HistoriaEras website publication state");
    }

    private static void storeProperties(Path path, Properties values, String comment) {
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                values.store(writer, comment);
            }
            moveAtomic(temporary, path);
        } catch (IOException exception) {
            HistoriaEras.LOGGER.error("Could not write {}", path, exception);
        }
    }

    private static void atomicWrite(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        moveAtomic(temporary, path);
    }

    private static void moveAtomic(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static long secondsToTicks(String raw, int fallback) {
        try {
            return Math.max(20L, Long.parseLong(raw) * 20L);
        } catch (Exception ignored) {
            return fallback * 20L;
        }
    }

    private static String value(Properties values, String key, String fallback) {
        String configured = values.getProperty(key, "").strip();
        return configured.isEmpty() ? fallback : configured;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String safeFailure(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        return message.length() > 480 ? message.substring(0, 480) : message;
    }

    private record Settings(
            boolean enabled, String treeEndpoint, String publicationsEndpoint,
            String sourceKey, String secret, long pollTicks, long publishTicks
    ) { }

    private record BridgeSettings(String endpoint, String sourceKey, String secret, boolean enabled) {
        private static final BridgeSettings EMPTY = new BridgeSettings(
                "https://historiamc.com/api/minecraft/tech-tree", "historia-primary", "", false);
    }
}
