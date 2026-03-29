package fr.enimaloc.gtd.file;

import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import net.dv8tion.jda.api.entities.Guild;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class GuildFileTomlTest {

    private static final TomlMapper MAPPER = new TomlMapper();

    @Test
    void roundTrip_preservesAllTopLevelFields() throws IOException {
        GuildFile original = new GuildFile();
        original.id = 123456789012345678L;
        original.banList = List.of(111L, 222L);

        original.profil.name = "My Server";
        original.profil.icon = "https://cdn.discordapp.com/icons/123/abc.png";
        original.profil.bannerUrl = "https://cdn.discordapp.com/banners/123/xyz.png";
        original.profil.bannerId = "xyz";
        original.profil.description = "A test server";
        original.profil.ownerId = 999888777666555444L;
        original.profil.vanityCode = "myserver";
        original.profil.splashUrl = "https://cdn.discordapp.com/splashes/123/splash.png";

        original.engagement.sendMessageOnJoin = true;
        original.engagement.canWelcomedWithStickers = false;
        original.engagement.sendMessageWhenBoost = true;
        original.engagement.sendTipsAboutConfig = false;
        original.engagement.systemChannel = 111222333444555666L;
        original.engagement.defaultNotification = Guild.NotificationLevel.ALL_MESSAGES;
        original.engagement.afkChannel = 777888999000111222L;
        original.engagement.afkTimeout = 300;
        original.engagement.enabledWidget = true;
        original.engagement.defaultChannelId = 333444555666777888L;

        original.boost.displayBoostBar = true;
        original.boost.unlockBanner = false;
        original.boost.unlockCustomInviteUrl = true;

        original.access.serverRule = true;

        original.security.verificationLevel = Guild.VerificationLevel.MEDIUM;
        original.security.explicitContentLevel = Guild.ExplicitContentLevel.ALL;
        original.security.mfaLevel = Guild.MFALevel.TWO_FACTOR_AUTH;
        original.security.nsfwLevel = Guild.NSFWLevel.DEFAULT;

        original.community.enabled = true;
        original.community.rulesChannel = 100200300400500600L;
        original.community.communityUpdateChannel = 200300400500600700L;
        original.community.securityChannel = 300400500600700800L;
        original.community.language = Locale.ENGLISH;
        original.community.description = "Community description";

        String toml = MAPPER.writeValueAsString(original);
        GuildFile restored = MAPPER.readValue(toml, GuildFile.class);

        assertEquals(original.id, restored.id);
        assertEquals(original.profil.name, restored.profil.name);
        assertEquals(original.profil.ownerId, restored.profil.ownerId);
        assertEquals(original.profil.vanityCode, restored.profil.vanityCode);
        assertEquals(original.profil.splashUrl, restored.profil.splashUrl);
        assertEquals(original.engagement.defaultChannelId, restored.engagement.defaultChannelId);
        assertEquals(original.engagement.defaultNotification, restored.engagement.defaultNotification);
        assertEquals(original.engagement.afkTimeout, restored.engagement.afkTimeout);
        assertEquals(original.boost.displayBoostBar, restored.boost.displayBoostBar);
        assertEquals(original.boost.unlockCustomInviteUrl, restored.boost.unlockCustomInviteUrl);
        assertEquals(original.access.serverRule, restored.access.serverRule);
        assertEquals(original.security.verificationLevel, restored.security.verificationLevel);
        assertEquals(original.security.explicitContentLevel, restored.security.explicitContentLevel);
        assertEquals(original.security.mfaLevel, restored.security.mfaLevel);
        assertEquals(original.security.nsfwLevel, restored.security.nsfwLevel);
        assertEquals(original.community.enabled, restored.community.enabled);
        assertEquals(original.community.language, restored.community.language);
        assertEquals(original.banList, restored.banList);
    }
}
