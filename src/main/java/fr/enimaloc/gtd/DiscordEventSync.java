package fr.enimaloc.gtd;

import fr.enimaloc.gtd.asset.AssetDownloader;
import fr.enimaloc.gtd.file.*;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.update.GenericChannelUpdateEvent;
import net.dv8tion.jda.api.events.emoji.EmojiAddedEvent;
import net.dv8tion.jda.api.events.emoji.EmojiRemovedEvent;
import net.dv8tion.jda.api.events.emoji.update.GenericEmojiUpdateEvent;
import net.dv8tion.jda.api.events.guild.update.GenericGuildUpdateEvent;
import net.dv8tion.jda.api.events.role.RoleCreateEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.role.update.GenericRoleUpdateEvent;
import net.dv8tion.jda.api.events.sticker.GuildStickerAddedEvent;
import net.dv8tion.jda.api.events.sticker.GuildStickerRemovedEvent;
import net.dv8tion.jda.api.events.sticker.update.GenericGuildStickerUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class DiscordEventSync extends ListenerAdapter {

    private final Map<Long, Server> servers;

    public DiscordEventSync(Map<Long, Server> servers) {
        this.servers = servers;
    }

    // ── Guild ─────────────────────────────────────────────────────────────────

    @Override
    public void onGenericGuildUpdate(GenericGuildUpdateEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        writeFile(server.gitPath().resolve(GuildFile.FILE_PATH), POJOUtils.parse(event.getGuild()));
        server.gitOps().scheduleCommit(
            event.getGuild().getIdLong(),
            "guild: update",
            event.getGuild(),
            ActionType.GUILD_UPDATE);
    }

    // ── Roles ─────────────────────────────────────────────────────────────────

    @Override
    public void onRoleCreate(RoleCreateEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        writeFile(server.gitPath().resolve(RoleFile.FILE_PATH.formatted(event.getRole().getIdLong())),
                POJOUtils.parse(event.getRole()));
        server.gitOps().scheduleCommit(
            event.getGuild().getIdLong(),
            "role: create " + event.getRole().getName(),
            event.getGuild(),
            ActionType.ROLE_CREATE);
    }

    @Override
    public void onGenericRoleUpdate(GenericRoleUpdateEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        writeFile(server.gitPath().resolve(RoleFile.FILE_PATH.formatted(event.getRole().getIdLong())),
                POJOUtils.parse(event.getRole()));
        server.gitOps().scheduleCommit(
            event.getGuild().getIdLong(),
            "role: update " + event.getRole().getName(),
            event.getGuild(),
            ActionType.ROLE_UPDATE);
    }

    @Override
    public void onRoleDelete(RoleDeleteEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        deleteFile(server.gitPath().resolve(RoleFile.FILE_PATH.formatted(event.getRole().getIdLong())));
        server.gitOps().scheduleCommit(
            event.getGuild().getIdLong(),
            "role: delete " + event.getRole().getName(),
            event.getGuild(),
            ActionType.ROLE_DELETE);
    }

    // ── Categories + Channels + Threads ──────────────────────────────────────

    @Override
    public void onChannelCreate(ChannelCreateEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        var channel = event.getChannel();
        String commitMsg;
        ActionType auditType;
        if (channel instanceof Category cat) {
            writeFile(server.gitPath().resolve(CategoryFile.FILE_PATH.formatted(cat.getIdLong())),
                    POJOUtils.parse(cat));
            commitMsg = "category: create " + cat.getName();
            auditType = ActionType.CHANNEL_CREATE;
        } else if (channel instanceof ThreadChannel thread) {
            long parentId = thread.getParentChannel().getIdLong();
            Path path = server.gitPath().resolve(
                    ThreadChannelFile.FILE_PATH.formatted(parentId, thread.getIdLong()));
            writeFile(path, POJOUtils.parseThread(thread));
            commitMsg = "thread: create " + thread.getName();
            auditType = ActionType.THREAD_CREATE;
        } else if (channel instanceof GuildChannel gc) {
            ChannelFile file = POJOUtils.parseChannel(gc);
            if (file == null) return;
            writeFile(server.gitPath().resolve(ChannelFile.FILE_PATH.formatted(gc.getIdLong())), file);
            commitMsg = "channel: create " + gc.getName();
            auditType = ActionType.CHANNEL_CREATE;
        } else {
            return;
        }
        server.gitOps().scheduleCommit(
            event.getGuild().getIdLong(), commitMsg, event.getGuild(), auditType);
    }

    @Override
    public void onGenericChannelUpdate(GenericChannelUpdateEvent<?> event) {
        if (!(event.getChannel() instanceof GuildChannel gc)) return;
        Server server = servers.get(gc.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        String commitMsg;
        ActionType auditType;
        if (gc instanceof Category cat) {
            writeFile(server.gitPath().resolve(CategoryFile.FILE_PATH.formatted(cat.getIdLong())),
                    POJOUtils.parse(cat));
            commitMsg = "category: update " + cat.getName();
            auditType = ActionType.CHANNEL_UPDATE;
        } else if (gc instanceof ThreadChannel thread) {
            long parentId = thread.getParentChannel().getIdLong();
            Path path = server.gitPath().resolve(
                    ThreadChannelFile.FILE_PATH.formatted(parentId, thread.getIdLong()));
            writeFile(path, POJOUtils.parseThread(thread));
            commitMsg = "thread: update " + thread.getName();
            auditType = ActionType.THREAD_UPDATE;
        } else {
            ChannelFile file = POJOUtils.parseChannel(gc);
            if (file == null) return;
            writeFile(server.gitPath().resolve(ChannelFile.FILE_PATH.formatted(gc.getIdLong())), file);
            commitMsg = "channel: update " + gc.getName();
            auditType = ActionType.CHANNEL_UPDATE;
        }
        server.gitOps().scheduleCommit(
            gc.getGuild().getIdLong(), commitMsg, gc.getGuild(), auditType);
    }

    @Override
    public void onChannelDelete(ChannelDeleteEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        long id = event.getChannel().getIdLong();
        String commitMsg;
        ActionType auditType;
        if (event.getChannel() instanceof Category cat) {
            deleteFile(server.gitPath().resolve(CategoryFile.FILE_PATH.formatted(id)));
            commitMsg = "category: delete " + cat.getName();
            auditType = ActionType.CHANNEL_DELETE;
        } else if (event.getChannel() instanceof ThreadChannel thread) {
            long parentId = thread.getParentChannel().getIdLong();
            deleteFile(server.gitPath().resolve(
                    ThreadChannelFile.FILE_PATH.formatted(parentId, id)));
            commitMsg = "thread: delete " + thread.getName();
            auditType = ActionType.THREAD_DELETE;
        } else {
            deleteFile(server.gitPath().resolve(ChannelFile.FILE_PATH.formatted(id)));
            commitMsg = "channel: delete " + event.getChannel().getName();
            auditType = ActionType.CHANNEL_DELETE;
        }
        server.gitOps().scheduleCommit(
            event.getGuild().getIdLong(), commitMsg, event.getGuild(), auditType);
    }

    // ── Emojis ────────────────────────────────────────────────────────────────

    @Override
    public void onEmojiAdded(EmojiAddedEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        EmoteFile ef = POJOUtils.parse(event.getEmoji());
        writeFile(server.gitPath().resolve(EmoteFile.FILE_PATH.formatted(event.getEmoji().getIdLong())), ef);
        if (ef.url != null)
            AssetDownloader.download(ef.url,
                server.gitPath().resolve("emojis/" + event.getEmoji().getIdLong() + ".webp"));
        server.gitOps().scheduleCommit(
            event.getGuild().getIdLong(),
            "emoji: add " + event.getEmoji().getName(),
            event.getGuild(),
            ActionType.EMOJI_CREATE);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void onGenericEmojiUpdate(GenericEmojiUpdateEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        EmoteFile ef = POJOUtils.parse(event.getEmoji());
        writeFile(server.gitPath().resolve(EmoteFile.FILE_PATH.formatted(event.getEmoji().getIdLong())), ef);
        if (ef.url != null)
            AssetDownloader.download(ef.url,
                server.gitPath().resolve("emojis/" + event.getEmoji().getIdLong() + ".webp"));
        server.gitOps().scheduleCommit(
            event.getGuild().getIdLong(),
            "emoji: update " + event.getEmoji().getName(),
            event.getGuild(),
            ActionType.EMOJI_UPDATE);
    }

    @Override
    public void onEmojiRemoved(EmojiRemovedEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        deleteFile(server.gitPath().resolve(EmoteFile.FILE_PATH.formatted(event.getEmoji().getIdLong())));
        server.gitOps().scheduleCommit(
            event.getGuild().getIdLong(),
            "emoji: remove " + event.getEmoji().getName(),
            event.getGuild(),
            ActionType.EMOJI_DELETE);
    }

    // ── Stickers ──────────────────────────────────────────────────────────────

    @Override
    public void onGuildStickerAdded(GuildStickerAddedEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        StickerFile sf = POJOUtils.parse(event.getSticker());
        writeFile(server.gitPath().resolve(StickerFile.FILE_PATH.formatted(event.getSticker().getIdLong())), sf);
        if (sf.url != null)
            AssetDownloader.download(sf.url,
                server.gitPath().resolve("stickers/" + event.getSticker().getIdLong() + ".webp"));
        server.gitOps().scheduleCommit(
            event.getGuild().getIdLong(),
            "sticker: add " + event.getSticker().getName(),
            event.getGuild(),
            ActionType.STICKER_CREATE);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void onGenericGuildStickerUpdate(GenericGuildStickerUpdateEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        StickerFile sf = POJOUtils.parse(event.getSticker());
        writeFile(server.gitPath().resolve(StickerFile.FILE_PATH.formatted(event.getSticker().getIdLong())), sf);
        if (sf.url != null)
            AssetDownloader.download(sf.url,
                server.gitPath().resolve("stickers/" + event.getSticker().getIdLong() + ".webp"));
        server.gitOps().scheduleCommit(
            event.getGuild().getIdLong(),
            "sticker: update " + event.getSticker().getName(),
            event.getGuild(),
            ActionType.STICKER_UPDATE);
    }

    @Override
    public void onGuildStickerRemoved(GuildStickerRemovedEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        deleteFile(server.gitPath().resolve(StickerFile.FILE_PATH.formatted(event.getSticker().getIdLong())));
        server.gitOps().scheduleCommit(
            event.getGuild().getIdLong(),
            "sticker: remove " + event.getSticker().getName(),
            event.getGuild(),
            ActionType.STICKER_DELETE);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void writeFile(Path path, Object file) {
        try {
            Files.createDirectories(path.getParent());
            GTD.MAPPER.writer().writeValue(path.toFile(), file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
