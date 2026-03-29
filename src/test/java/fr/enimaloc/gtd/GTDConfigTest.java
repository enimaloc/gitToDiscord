package fr.enimaloc.gtd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GTDConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveConfig_existingFile_readsIt() throws IOException {
        File configFile = tempDir.resolve("config.toml").toFile();
        GTD.Config written = new GTD.Config();
        written.botToken = "file-token";
        written.dataPath = "/custom/data";
        GTD.MAPPER.writer().writeValue(configFile, written);

        GTD.Config config = GTD.resolveConfig(configFile.getAbsolutePath(), Map.of());

        assertEquals("file-token", config.botToken);
        assertEquals("/custom/data", config.dataPath);
    }

    @Test
    void resolveConfig_noFile_withEnvVars_generatesConfig() throws IOException {
        String tomlPath = tempDir.resolve("config.toml").toString();
        Map<String, String> env = Map.of(
            "DISCORD_TOKEN", "env-discord-token",
            "GTD_DATA_PATH", "/env/data"
        );

        GTD.Config config = GTD.resolveConfig(tomlPath, env);

        assertEquals("env-discord-token", config.botToken);
        assertEquals("/env/data", config.dataPath);
        assertTrue(new File(tomlPath).exists(), "config.toml should have been written to disk");
    }

    @Test
    void resolveConfig_noFile_noGTDDataPath_defaultsToAppData() throws IOException {
        String tomlPath = tempDir.resolve("config.toml").toString();
        Map<String, String> env = Map.of("DISCORD_TOKEN", "some-token");

        GTD.Config config = GTD.resolveConfig(tomlPath, env);

        assertEquals("/app/data", config.dataPath);
    }

    @Test
    void resolveConfig_noFile_noDiscordToken_throwsIllegalStateException() {
        String tomlPath = tempDir.resolve("config.toml").toString();

        assertThrows(IllegalStateException.class,
            () -> GTD.resolveConfig(tomlPath, Map.of()));
    }
}
