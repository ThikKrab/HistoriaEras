package com.historiamc.townyeras.tech;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class HistoriaErasBrandingTest {
    @Test void localizedNameAndKeybindsUseHistoriaErasWithoutChangingSavedKeys() throws Exception {
        var lang = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/townyeras/lang/en_us.json"))).getAsJsonObject();
        assertEquals("HistoriaEras", lang.get("key.categories.townyeras").getAsString());
        assertEquals("Open HistoriaEras Dashboard", lang.get("key.townyeras.open_dashboard").getAsString());
        assertEquals("HistoriaEras", lang.get("screen.townyeras.dashboard").getAsString());
    }

    @Test void sourceBrandingIsCompleteAndNavigationLogoFits() throws Exception {
        try (var paths = Files.walk(Path.of("src/main"))) {
            for (var path : paths.filter(Files::isRegularFile).filter(p ->
                    p.toString().endsWith(".java") || p.toString().endsWith(".json")
                    || p.toString().endsWith(".toml")).toList()) {
                String content = Files.readString(path);
                assertFalse(content.contains("Towny" + "Eras"), path.toString());
                assertFalse(content.contains("TOWNY"), path.toString());
            }
        }
        String screen = Files.readString(Path.of(
                "src/main/java/com/historiamc/townyeras/client/screen/HistoriaErasScreen.java"));
        assertTrue(screen.contains("drawFittedCenteredString(graphics, \"HISTORIA\""));
        assertTrue(screen.contains("\"HE\""));
    }

    @Test void existingProgressAndRegistryIdentityArePreserved() throws Exception {
        String properties = Files.readString(Path.of("gradle.properties"));
        assertTrue(properties.contains("mod_id=townyeras"));
        assertTrue(properties.contains("mod_name=HistoriaEras"));
        String data = Files.readString(Path.of(
                "src/main/java/com/historiamc/townyeras/server/HistoriaErasSavedData.java"));
        assertTrue(data.contains("\"townyeras_progress\""));
        assertEquals(77, TechRegistry.load().nodes().size());
    }
}
