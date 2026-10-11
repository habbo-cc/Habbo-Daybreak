package com.eu.habbo.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void booleanReadsOnceAndHonoursTheSuppliedDefault() throws Exception {
        Path config = this.tempDir.resolve("config.ini");
        Files.writeString(config, "feature.enabled=1\nfeature.disabled=false\n");
        ConfigurationManager manager = new ConfigurationManager(config.toString());

        assertTrue(manager.getBoolean("feature.enabled", false));
        assertFalse(manager.getBoolean("feature.disabled", true));
        assertTrue(manager.getBoolean("feature.missing.enabled", true));
        assertFalse(manager.getBoolean("feature.missing.disabled", false));
    }
}
