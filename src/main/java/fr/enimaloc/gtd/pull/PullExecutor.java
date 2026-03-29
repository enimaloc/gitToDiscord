package fr.enimaloc.gtd.pull;

import fr.enimaloc.gtd.file.*;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.*;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PullExecutor {

    private final Guild guild;

    public PullExecutor(Guild guild) {
        this.guild = guild;
    }

    // ── Guild ─────────────────────────────────────────────────────────────────

    public void applyGuild(GuildFile file) {
        var manager = guild.getManager()
            .setName(file.profil.name)
            .setBoostProgressBarEnabled(file.boost.displayBoostBar)
            .setDefaultNotificationLevel(file.engagement.defaultNotification);

        if (file.profil.description != null && guild.getFeatures().contains("VERIFIED"))
            manager = manager.setDescription(file.profil.description);
        if (file.security.verificationLevel != null)
            manager = manager.setVerificationLevel(file.security.verificationLevel);
        if (file.security.explicitContentLevel != null)
            manager = manager.setExplicitContentLevel(file.security.explicitContentLevel);
        if (file.engagement.afkTimeout > 0) {
            Guild.Timeout timeout = Arrays.stream(Guild.Timeout.values())
                    .filter(t -> t.getSeconds() == file.engagement.afkTimeout)
                    .findFirst().orElse(Guild.Timeout.SECONDS_300);
            manager = manager.setAfkTimeout(timeout);
        }

        manager.queue();
    }

    // ── Roles ─────────────────────────────────────────────────────────────────

    public void applyRole(long discordId, RoleFile file) {
        Role role = guild.getRoleById(discordId);
        if (role == null || role.getPosition() >= guild.getBotRole().getPosition()) return;
        role.getManager()
            .setName(file.name)
            .setPermissions(permissionsFromMap(file.permissions))
            .setColor(file.primaryColor)
            .setHoisted(file.hoist)
            .setMentionable(file.mentionable)
            .queue();
    }

    public Role createRole(RoleFile file) {
        return guild.createRole()
            .setName(file.name)
            .setPermissions(permissionsFromMap(file.permissions))
            .setColor(file.primaryColor)
            .setHoisted(file.hoist)
            .setMentionable(file.mentionable)
            .complete();
    }

    // ── Categories ────────────────────────────────────────────────────────────

    public void applyCategory(long discordId, CategoryFile file) {
        Category cat = guild.getCategoryById(discordId);
        if (cat == null) return;
        var manager = cat.getManager().setName(file.name);
        applyPermissionOverrides(manager, file.roleOverride, file.memberOverride);
        manager.queue();
    }

    public Category createCategory(CategoryFile file) {
        Category cat = guild.createCategory(file.name).complete();
        var manager = cat.getManager();
        applyPermissionOverrides(manager, file.roleOverride, file.memberOverride);
        manager.queue();
        return cat;
    }

    // ── Channels ──────────────────────────────────────────────────────────────

    public void applyChannel(long discordId, ChannelFile file) {
        switch (file.type) {
            case "text"  -> { TextChannel  c = guild.getTextChannelById(discordId);  if (c != null) applyTextChannel(c,  (TextChannelFile)  file); }
            case "voice" -> { VoiceChannel c = guild.getVoiceChannelById(discordId); if (c != null) applyVoiceChannel(c, (VoiceChannelFile) file); }
            case "news"  -> { NewsChannel  c = guild.getNewsChannelById(discordId);  if (c != null) applyNewsChannel(c,  (NewsChannelFile)  file); }
            case "stage" -> { StageChannel c = guild.getStageChannelById(discordId); if (c != null) applyStageChannel(c, (StageChannelFile) file); }
            case "forum" -> { ForumChannel c = guild.getForumChannelById(discordId); if (c != null) applyForumChannel(c, (ForumChannelFile) file); }
            case "media" -> { MediaChannel c = guild.getMediaChannelById(discordId); if (c != null) applyMediaChannel(c, (MediaChannelFile) file); }
        }
    }

    public void createChannel(ChannelFile file) {
        Category parent = file.parentCategoryId != 0
                ? guild.getCategoryById(file.parentCategoryId) : null;

        switch (file.type) {
            case "text" -> {
                TextChannelFile f = (TextChannelFile) file;
                TextChannel c = guild.createTextChannel(f.name, parent).complete();
                c.getManager().setTopic(f.topic).setSlowmode(f.slowmode).setNSFW(f.nsfw).queue();
            }
            case "voice" -> {
                VoiceChannelFile f = (VoiceChannelFile) file;
                VoiceChannel c = guild.createVoiceChannel(f.name, parent).complete();
                c.getManager().setBitrate(f.bitrate).setUserLimit(f.userLimit).queue();
            }
            case "news" -> {
                NewsChannelFile f = (NewsChannelFile) file;
                guild.createNewsChannel(f.name, parent).complete();
            }
            case "stage" -> {
                guild.createStageChannel(file.name, parent).complete();
            }
            case "forum" -> {
                guild.createForumChannel(file.name, parent).complete();
            }
            case "media" -> {
                guild.createMediaChannel(file.name, parent).complete();
            }
        }
    }

    private void applyTextChannel(TextChannel channel, TextChannelFile file) {
        var manager = channel.getManager()
            .setName(file.name)
            .setTopic(file.topic)
            .setSlowmode(file.slowmode)
            .setNSFW(file.nsfw);
        applyPermissionOverrides(manager, file.roleOverride, file.memberOverride);
        manager.queue();
    }

    private void applyVoiceChannel(VoiceChannel channel, VoiceChannelFile file) {
        var manager = channel.getManager()
            .setName(file.name)
            .setBitrate(file.bitrate)
            .setUserLimit(file.userLimit);
        applyPermissionOverrides(manager, file.roleOverride, file.memberOverride);
        manager.queue();
    }

    private void applyNewsChannel(NewsChannel channel, NewsChannelFile file) {
        var manager = channel.getManager()
            .setName(file.name)
            .setTopic(file.topic)
            .setNSFW(file.nsfw);
        applyPermissionOverrides(manager, file.roleOverride, file.memberOverride);
        manager.queue();
    }

    private void applyStageChannel(StageChannel channel, StageChannelFile file) {
        channel.getManager()
            .setName(file.name)
            .setBitrate(file.bitrate)
            .queue();
    }

    private void applyForumChannel(ForumChannel channel, ForumChannelFile file) {
        var manager = channel.getManager()
            .setName(file.name)
            .setTopic(file.topic)
            .setSlowmode(file.slowmode);
        if (file.layout != null && !file.layout.equals("UNKNOWN")) {
            manager = manager.setDefaultLayout(ForumChannel.Layout.valueOf(file.layout));
        }
        applyPermissionOverrides(manager, file.roleOverride, file.memberOverride);
        manager.queue();
    }

    private void applyMediaChannel(MediaChannel channel, MediaChannelFile file) {
        channel.getManager()
            .setName(file.name)
            .setTopic(file.topic)
            .setSlowmode(file.slowmode)
            .queue();
    }

    // ── Deletions ─────────────────────────────────────────────────────────────

    public void deleteRole(long discordId) {
        Role role = guild.getRoleById(discordId);
        if (role == null || role.getPosition() >= guild.getBotRole().getPosition() || role.isManaged() || role.isPublicRole()) return;
        role.delete().queue();
    }

    public void deleteCategory(long discordId) {
        Category cat = guild.getCategoryById(discordId);
        if (cat != null) cat.delete().queue();
    }

    public void deleteChannel(long discordId) {
        var channel = guild.getGuildChannelById(discordId);
        if (channel != null) channel.delete().queue();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Set<Permission> permissionsFromMap(Map<String, Boolean> permissions) {
        return Arrays.stream(Permission.values())
            .filter(p -> permissions.getOrDefault(p.getName(), false))
            .collect(Collectors.toSet());
    }

    private static void applyPermissionOverrides(
            net.dv8tion.jda.api.managers.channel.attribute.IPermissionContainerManager<?, ?> manager,
            Map<Long, Map<String, Integer>> roleOverrides,
            Map<Long, Map<String, Integer>> memberOverrides) {

        roleOverrides.forEach((roleId, perms) -> {
            long allow = computeRaw(perms, 1);
            long deny  = computeRaw(perms, -1);
            manager.putRolePermissionOverride(roleId, allow, deny);
        });

        memberOverrides.forEach((memberId, perms) -> {
            long allow = computeRaw(perms, 1);
            long deny  = computeRaw(perms, -1);
            manager.putMemberPermissionOverride(memberId, allow, deny);
        });
    }

    private static long computeRaw(Map<String, Integer> perms, int target) {
        return Arrays.stream(Permission.values())
            .filter(p -> perms.getOrDefault(p.getName(), 0) == target)
            .mapToLong(Permission::getRawValue)
            .reduce(0L, (a, b) -> a | b);
    }
}
