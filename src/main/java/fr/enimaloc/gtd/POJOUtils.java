package fr.enimaloc.gtd;

import fr.enimaloc.gtd.GuildFeature;
import fr.enimaloc.gtd.file.*;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.Widget;
import net.dv8tion.jda.api.entities.channel.concrete.*;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildChannel;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.entities.channel.ChannelFlag;
import net.dv8tion.jda.api.entities.guild.SystemChannelFlag;
import net.dv8tion.jda.api.entities.sticker.GuildSticker;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.utils.WidgetUtil;
import net.dv8tion.jda.internal.entities.GuildImpl;
import net.dv8tion.jda.internal.entities.RoleImpl;
import net.dv8tion.jda.internal.entities.emoji.RichCustomEmojiImpl;
import net.dv8tion.jda.internal.entities.sticker.GuildStickerImpl;

import java.util.Arrays;
import java.util.stream.Collectors;

public class POJOUtils {

    // ── Guild ─────────────────────────────────────────────────────────────────

    public static GuildFile parse(Guild guild) {
        return parse((GuildImpl) guild);
    }

    public static GuildFile parse(GuildImpl guild) {
        Widget widget = null;
        try {
            widget = WidgetUtil.getWidget(guild.getIdLong());
        } catch (RateLimitedException ign) {}

        GuildFile file = new GuildFile();

        file.id = guild.getIdLong();
        file.profil.name = guild.getName();
        file.profil.icon = guild.getIconUrl();
        file.profil.bannerId = guild.getBannerId();
        file.profil.bannerUrl = guild.getBannerUrl();
        file.profil.description = guild.getDescription();
        file.profil.ownerId = guild.getOwnerIdLong();
        file.profil.vanityCode = guild.getVanityCode();
        file.profil.splashUrl = guild.getSplashUrl();

        file.engagement.sendMessageOnJoin = !guild.getSystemChannelFlags().contains(SystemChannelFlag.SUPPRESS_JOIN_NOTIFICATIONS);
        file.engagement.canWelcomedWithStickers = !guild.getSystemChannelFlags().contains(SystemChannelFlag.SUPPRESS_JOIN_NOTIFICATION_REPLIES);
        file.engagement.sendMessageWhenBoost = !guild.getSystemChannelFlags().contains(SystemChannelFlag.SUPPRESS_PREMIUM_SUBSCRIPTIONS);
        file.engagement.sendTipsAboutConfig = !guild.getSystemChannelFlags().contains(SystemChannelFlag.SUPPRESS_GUILD_REMINDER_NOTIFICATIONS);
        file.engagement.systemChannel = guild.getSystemChannel() != null ? guild.getSystemChannel().getIdLong() : 0;
        file.engagement.defaultNotification = guild.getDefaultNotificationLevel();
        file.engagement.afkChannel = guild.getAfkChannel() != null ? guild.getAfkChannel().getIdLong() : 0;
        file.engagement.afkTimeout = guild.getAfkTimeout().getSeconds();
        file.engagement.enabledWidget = widget != null && widget.isAvailable();
        file.engagement.defaultChannelId = guild.getDefaultChannel() != null ? guild.getDefaultChannel().getIdLong() : 0;

        file.boost.displayBoostBar = guild.isBoostProgressBarEnabled();
        file.boost.unlockBanner = GuildFeature.has(guild, GuildFeature.BANNER);
        file.boost.unlockCustomInviteUrl = GuildFeature.has(guild, GuildFeature.VANITY_URL);

        file.security.verificationLevel = guild.getVerificationLevel();
        file.security.explicitContentLevel = guild.getExplicitContentLevel();
        file.security.mfaLevel = guild.getRequiredMFALevel();
        file.security.nsfwLevel = guild.getNSFWLevel();

        file.access.serverRule = GuildFeature.has(guild, GuildFeature.COMMUNITY);

        file.banList = guild.retrieveBanList().complete().stream().map(ban -> ban.getUser().getIdLong()).toList();

        file.community.enabled = GuildFeature.has(guild, GuildFeature.COMMUNITY);
        file.community.rulesChannel = guild.getRulesChannel() != null ? guild.getRulesChannel().getIdLong() : 0;
        file.community.communityUpdateChannel = guild.getCommunityUpdatesChannel() != null ? guild.getCommunityUpdatesChannel().getIdLong() : 0;
        file.community.securityChannel = guild.getSafetyAlertsChannel() != null ? guild.getSafetyAlertsChannel().getIdLong() : 0;
        file.community.language = guild.getLocale().toLocale();
        file.community.description = guild.getDescription();

        return file;
    }

    // ── Emoji ─────────────────────────────────────────────────────────────────

    public static EmoteFile parse(RichCustomEmoji emote) {
        return parse((RichCustomEmojiImpl) emote);
    }

    public static EmoteFile parse(RichCustomEmojiImpl emote) {
        EmoteFile file = new EmoteFile();
        file.id = emote.getIdLong();
        file.owner = emote.getOwner() != null ? emote.getOwner().getIdLong() : 0;
        file.animated = emote.isAnimated();
        file.available = emote.isAvailable();
        file.managed = emote.isManaged();
        file.name = emote.getName();
        file.url = emote.getImageUrl();
        file.allowedRoleIds = emote.getRoles().stream().map(role -> role.getIdLong()).toList();
        return file;
    }

    // ── Sticker ───────────────────────────────────────────────────────────────

    public static StickerFile parse(GuildSticker sticker) {
        return parse((GuildStickerImpl) sticker);
    }

    public static StickerFile parse(GuildStickerImpl sticker) {
        StickerFile file = new StickerFile();
        file.id = sticker.getIdLong();
        file.owner = sticker.getOwner() != null ? sticker.getOwner().getIdLong() : 0;
        file.tags = sticker.getTags();
        file.available = sticker.isAvailable();
        file.description = sticker.getDescription();
        file.name = sticker.getName();
        file.url = sticker.getIconUrl();
        return file;
    }

    // ── Role ──────────────────────────────────────────────────────────────────

    public static RoleFile parse(Role role) {
        return parse((RoleImpl) role);
    }

    public static RoleFile parse(RoleImpl role) {
        RoleFile file = new RoleFile();
        file.id = role.getIdLong();
        file.name = role.getName();
        file.permissions = Arrays.stream(Permission.values()).collect(Collectors.toMap(Permission::getName, role::hasPermission));
        file.iconUrl = role.getIcon() != null ? role.getIcon().getIconUrl() : null;
        file.primaryColor = role.getColors().getPrimaryRaw();
        file.secondaryColor = role.getColors().getSecondaryRaw();
        file.tertiaryColor = role.getColors().getTertiaryRaw();
        file.mentionable = role.isMentionable();
        file.hoist = role.isHoisted();
        file.position = role.getPosition();
        file.managed = role.isManaged();
        file.publicRole = role.isPublicRole();
        file.botId = role.getTags().getBotIdLong();
        file.isBot = role.getTags().isBot();
        file.isBoost = role.getTags().isBoost();
        file.isIntegration = role.getTags().isIntegration();
        file.isLinkedRole = role.getTags().isLinkedRole();
        return file;
    }

    // ── Channels dispatcher ───────────────────────────────────────────────────

    /**
     * Dispatche vers le parser approprié selon le type de canal.
     * Retourne null pour les ThreadChannel (utiliser parseThread à la place).
     */
    public static ChannelFile parseChannel(GuildChannel channel) {
        if (channel instanceof TextChannel c)  return parse(c);
        if (channel instanceof VoiceChannel c) return parse(c);
        if (channel instanceof NewsChannel c)  return parse(c);
        if (channel instanceof StageChannel c) return parse(c);
        if (channel instanceof ForumChannel c) return parse(c);
        if (channel instanceof MediaChannel c) return parse(c);
        return null;
    }

    // ── Category ──────────────────────────────────────────────────────────────

    public static CategoryFile parse(Category channel) {
        CategoryFile file = new CategoryFile();
        file.id = channel.getIdLong();
        file.name = channel.getName();
        file.position = channel.getPosition();
        channel.getMemberPermissionOverrides().forEach(override ->
            file.memberOverride.put(override.getPermissionHolder() != null ? override.getPermissionHolder().getIdLong() : 0,
                Arrays.stream(Permission.values()).collect(Collectors.toMap(Permission::getName,
                    p -> override.getAllowed().contains(p) ? 1 : override.getDenied().contains(p) ? -1 : 0))));
        channel.getRolePermissionOverrides().forEach(override ->
            file.roleOverride.put(override.getPermissionHolder() != null ? override.getPermissionHolder().getIdLong() : 0,
                Arrays.stream(Permission.values()).collect(Collectors.toMap(Permission::getName,
                    p -> override.getAllowed().contains(p) ? 1 : override.getDenied().contains(p) ? -1 : 0))));
        return file;
    }

    // ── TextChannel ───────────────────────────────────────────────────────────

    public static TextChannelFile parse(TextChannel channel) {
        TextChannelFile file = new TextChannelFile();
        parseStandardFields(channel, file);
        file.topic = channel.getTopic();
        file.slowmode = channel.getSlowmode();
        file.nsfw = channel.isNSFW();
        return file;
    }

    // ── VoiceChannel ──────────────────────────────────────────────────────────

    public static VoiceChannelFile parse(VoiceChannel channel) {
        VoiceChannelFile file = new VoiceChannelFile();
        parseStandardFields(channel, file);
        file.bitrate = channel.getBitrate();
        file.userLimit = channel.getUserLimit();
        file.region = channel.getRegion().getKey();
        return file;
    }

    // ── NewsChannel ───────────────────────────────────────────────────────────

    public static NewsChannelFile parse(NewsChannel channel) {
        NewsChannelFile file = new NewsChannelFile();
        parseStandardFields(channel, file);
        file.topic = channel.getTopic();
        file.nsfw = channel.isNSFW();
        return file;
    }

    // ── StageChannel ──────────────────────────────────────────────────────────

    public static StageChannelFile parse(StageChannel channel) {
        StageChannelFile file = new StageChannelFile();
        parseStandardFields(channel, file);
        file.bitrate = channel.getBitrate();
        return file;
    }

    // ── ForumChannel ──────────────────────────────────────────────────────────

    public static ForumChannelFile parse(ForumChannel channel) {
        ForumChannelFile file = new ForumChannelFile();
        parseStandardFields(channel, file);
        file.topic = channel.getTopic();
        file.slowmode = channel.getSlowmode();
        file.layout = channel.getDefaultLayout().name();
        channel.getAvailableTags().forEach(tag -> file.availableTags.add(tag.getName()));
        file.defaultReactionEmoji = channel.getDefaultReaction() != null ? channel.getDefaultReaction().getName() : null;
        file.defaultSortOrder = channel.getDefaultSortOrder().name();
        file.tagRequired = channel.getFlags().contains(ChannelFlag.REQUIRE_TAG);
        file.defaultThreadSlowmode = channel.getDefaultThreadSlowmode();
        return file;
    }

    // ── MediaChannel ──────────────────────────────────────────────────────────

    public static MediaChannelFile parse(MediaChannel channel) {
        MediaChannelFile file = new MediaChannelFile();
        parseStandardFields(channel, file);
        file.topic = channel.getTopic();
        file.slowmode = channel.getSlowmode();
        file.nsfw = channel.isNSFW();
        file.mediaDownloadHidden = channel.getFlags().contains(ChannelFlag.HIDE_MEDIA_DOWNLOAD_OPTIONS);
        return file;
    }

    // ── ThreadChannel ─────────────────────────────────────────────────────────

    public static ThreadChannelFile parseThread(ThreadChannel thread) {
        ThreadChannelFile file = new ThreadChannelFile();
        file.id = thread.getIdLong();
        file.name = thread.getName();
        file.parentChannelId = thread.getParentChannel().getIdLong();
        file.archived = thread.isArchived();
        file.locked = thread.isLocked();
        file.autoArchiveDurationMinutes = thread.getAutoArchiveDuration().getMinutes();
        thread.getMembers().forEach(member -> file.memberIds.add(member.getIdLong()));
        file.pinned = thread.getFlags().contains(ChannelFlag.PINNED);
        file._public = thread.isPublic();
        file.invitable = !thread.isPublic() && thread.isInvitable();
        thread.getAppliedTags().forEach(tag -> file.appliedTags.add(tag.getName()));
        file.ownerId = thread.getOwnerIdLong();
        return file;
    }

    // ── Shared helper ─────────────────────────────────────────────────────────

    private static void parseStandardFields(StandardGuildChannel channel, ChannelFile file) {
        file.id = channel.getIdLong();
        file.name = channel.getName();
        file.position = channel.getPosition();
        file.parentCategoryId = channel.getParentCategoryIdLong();
        file.positionInCategory = channel.getPositionInCategory();
        channel.getMemberPermissionOverrides().forEach(override ->
            file.memberOverride.put(override.getPermissionHolder() != null ? override.getPermissionHolder().getIdLong() : 0,
                Arrays.stream(Permission.values()).collect(Collectors.toMap(Permission::getName,
                    p -> override.getAllowed().contains(p) ? 1 : override.getDenied().contains(p) ? -1 : 0))));
        channel.getRolePermissionOverrides().forEach(override ->
            file.roleOverride.put(override.getPermissionHolder() != null ? override.getPermissionHolder().getIdLong() : 0,
                Arrays.stream(Permission.values()).collect(Collectors.toMap(Permission::getName,
                    p -> override.getAllowed().contains(p) ? 1 : override.getDenied().contains(p) ? -1 : 0))));
    }
}
