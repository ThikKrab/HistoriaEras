package com.historiamc.townyeras.client;

import com.historiamc.townyeras.HistoriaEras;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Client-only preferences which must never affect server progression rules. */
public final class HistoriaErasClientPreferences {
    private static final String FILE_NAME = "townyeras-client.properties";
    private static final String CALENDAR_BAR_VISIBLE = "calendarBarVisible";
    private static final Properties VALUES = new Properties();
    private static boolean loaded;

    private HistoriaErasClientPreferences() {
    }

    public static synchronized boolean calendarBarVisible() {
        load();
        return Boolean.parseBoolean(VALUES.getProperty(CALENDAR_BAR_VISIBLE, "true"));
    }

    public static synchronized boolean toggleCalendarBar() {
        boolean visible = !calendarBarVisible();
        VALUES.setProperty(CALENDAR_BAR_VISIBLE, Boolean.toString(visible));
        save();
        return visible;
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = path();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (InputStream input = Files.newInputStream(path)) {
            VALUES.load(input);
        } catch (IOException exception) {
            HistoriaEras.LOGGER.warn("Could not read client preferences from {}", path, exception);
        }
    }

    private static void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                VALUES.store(output, "HistoriaEras client display preferences");
            }
        } catch (IOException exception) {
            HistoriaEras.LOGGER.warn("Could not save client preferences to {}", path, exception);
        }
    }

    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
    }
}
