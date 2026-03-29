package fr.enimaloc.gtd.file;

import net.dv8tion.jda.api.entities.Guild;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GuildFile {
    public static final String NAME = "guild";
    public static final String FILE_PATH = NAME + ".toml";

    public long id;
    public Profil profil = new Profil();
    public Engagement engagement = new Engagement();
    public Boost boost = new Boost();
    public Access access = new Access();
    public Security security = new Security();
    public List<Long> banList = new ArrayList<>();
    public Community community = new Community();

    public static class Profil {
        public String name;
        public String icon;
        public String bannerUrl;
        public String bannerId;
        public String description;
        public long ownerId;
        public String vanityCode;
        public String splashUrl;
    }

    public static class Engagement {
        public boolean sendMessageOnJoin;
        public boolean canWelcomedWithStickers;
        public boolean sendMessageWhenBoost;
        public boolean sendTipsAboutConfig;
        public long systemChannel;
        public Guild.NotificationLevel defaultNotification;
        public long afkChannel;
        public int afkTimeout;
        public boolean enabledWidget;
        public long defaultChannelId;
    }

    public static class Boost {
        public boolean displayBoostBar;
        public boolean unlockBanner;
        public boolean unlockCustomInviteUrl;
    }

    public static class Access {
        public boolean serverRule;
    }

    public static class Security {
        public Guild.VerificationLevel verificationLevel;
        public Guild.ExplicitContentLevel explicitContentLevel;
        public Guild.MFALevel mfaLevel;
        public Guild.NSFWLevel nsfwLevel;
    }

    public static class Community {
        public boolean enabled;
        public long rulesChannel;
        public long communityUpdateChannel;
        public long securityChannel;
        public Locale language;
        public String description;
    }
}
