package fr.enimaloc.gtd.file;

import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RoleFileTomlTest {

    private static final TomlMapper MAPPER = new TomlMapper();

    @Test
    void roundTrip_preservesAllFields() throws IOException {
        RoleFile original = new RoleFile();
        original.id = 987654321098765432L;
        original.name = "Admin";
        original.permissions = Map.of("ADMINISTRATOR", true, "MANAGE_GUILD", false);
        original.iconUrl = "https://cdn.discordapp.com/role-icons/123/icon.png";
        original.primaryColor = 0xFF0000;
        original.secondaryColor = 0x00FF00;
        original.tertiaryColor = 0x0000FF;
        original.mentionable = true;
        original.hoist = false;
        original.position = 5;
        original.managed = false;
        original.publicRole = false;
        original.botId = 111222333444555666L;
        original.isBot = true;
        original.isBoost = false;
        original.isIntegration = false;
        original.isLinkedRole = false;

        String toml = MAPPER.writeValueAsString(original);
        RoleFile restored = MAPPER.readValue(toml, RoleFile.class);

        assertEquals(original.id, restored.id);
        assertEquals(original.name, restored.name);
        assertEquals(original.primaryColor, restored.primaryColor);
        assertEquals(original.mentionable, restored.mentionable);
        assertEquals(original.hoist, restored.hoist);
        assertEquals(original.position, restored.position);
        assertEquals(original.botId, restored.botId);
        assertEquals(original.isBot, restored.isBot);
        assertEquals(original.isBoost, restored.isBoost);
        assertEquals(original.isIntegration, restored.isIntegration);
        assertEquals(original.isLinkedRole, restored.isLinkedRole);
    }
}
