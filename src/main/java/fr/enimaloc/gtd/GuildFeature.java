package fr.enimaloc.gtd;

import net.dv8tion.jda.api.entities.Guild;

public final class GuildFeature {
    public static final String BANNER        = "BANNER";
    public static final String VANITY_URL    = "VANITY_URL";
    public static final String COMMUNITY     = "COMMUNITY";
    public static final String INVITE_SPLASH = "INVITE_SPLASH";
    public static final String VIP_REGIONS   = "VIP_REGIONS";

    private GuildFeature() {}

    public static boolean has(Guild guild, String feature) {
        return guild.getFeatures().contains(feature);
    }
}
