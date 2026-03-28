package fr.enimaloc.gtd;

import fr.enimaloc.gtd.asset.AssetDownloader;
import fr.enimaloc.gtd.file.*;
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
        if (server == null) return;
        writeFile(server.gitPath().resolve(GuildFile.FILE_PATH),
                POJOUtils.parse(event.getGuild()));
    }

    // ── Roles ─────────────────────────────────────────────────────────────────

    @Override
    public void onRoleCreate(RoleCreateEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null) return;
        writeFile(server.gitPath().resolve(RoleFile.FILE_PATH.formatted(event.getRole().getIdLong())),
                POJOUtils.parse(event.getRole()));
    }

    @Override
    public void onGenericRoleUpdate(GenericRoleUpdateEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null) return;
        writeFile(server.gitPath().resolve(RoleFile.FILE_PATH.formatted(event.getRole().getIdLong())),
                POJOUtils.parse(event.getRole()));
    }

    @Override
    public void onRoleDelete(RoleDeleteEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null) return;
        deleteFile(server.gitPath().resolve(RoleFile.FILE_PATH.formatted(event.getRole().getIdLong())));
    }

    // ── Categories + Channels + Threads ──────────────────────────────────────

    @Override
    public void onChannelCreate(ChannelCreateEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null) return;
        var channel = event.getChannel();
        if (channel instanceof Category cat) {
            writeFile(server.gitPath().resolve(CategoryFile.FILE_PATH.formatted(cat.getIdLong())),
                    POJOUtils.parse(cat));
        } else if (channel instanceof ThreadChannel thread) {
            long parentId = thread.getParentChannel().getIdLong();
            Path path = server.gitPath().resolve(
                    ThreadChannelFile.FILE_PATH.formatted(parentId, thread.getIdLong()));
            writeFile(path, POJOUtils.parseThread(thread));
        } else if (channel instanceof GuildChannel gc) {
            ChannelFile file = POJOUtils.parseChannel(gc);
            if (file != null)
                writeFile(server.gitPath().resolve(ChannelFile.FILE_PATH.formatted(gc.getIdLong())), file);
        }
    }

    @Override
    public void onGenericChannelUpdate(GenericChannelUpdateEvent<?> event) {
        if (!(event.getChannel() instanceof GuildChannel gc)) return;
        Server server = servers.get(gc.getGuild().getIdLong());
        if (server == null) return;
        if (gc instanceof Category cat) {
            writeFile(server.gitPath().resolve(CategoryFile.FILE_PATH.formatted(cat.getIdLong())),
                    POJOUtils.parse(cat));
        } else if (gc instanceof ThreadChannel thread) {
            long parentId = thread.getParentChannel().getIdLong();
            Path path = server.gitPath().resolve(
                    ThreadChannelFile.FILE_PATH.formatted(parentId, thread.getIdLong()));
            writeFile(path, POJOUtils.parseThread(thread));
        } else {
            ChannelFile file = POJOUtils.parseChannel(gc);
            if (file != null)
                writeFile(server.gitPath().resolve(ChannelFile.FILE_PATH.formatted(gc.getIdLong())), file);
        }
    }

    @Override
    public void onChannelDelete(ChannelDeleteEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null) return;
        long id = event.getChannel().getIdLong();
        if (event.getChannel() instanceof Category) {
            deleteFile(server.gitPath().resolve(CategoryFile.FILE_PATH.formatted(id)));
        } else if (event.getChannel() instanceof ThreadChannel thread) {
            long parentId = thread.getParentChannel().getIdLong();
            deleteFile(server.gitPath().resolve(
                    ThreadChannelFile.FILE_PATH.formatted(parentId, id)));
        } else {
            deleteFile(server.gitPath().resolve(ChannelFile.FILE_PATH.formatted(id)));
        }
    }

    // ── Emojis ────────────────────────────────────────────────────────────────

    @Override
    public void onEmojiAdded(EmojiAddedEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null) return;
        EmoteFile ef = POJOUtils.parse(event.getEmoji());
        writeFile(server.gitPath().resolve(EmoteFile.FILE_PATH.formatted(event.getEmoji().getIdLong())), ef);
        if (ef.url != null)
            AssetDownloader.download(ef.url,
                server.gitPath().resolve("emojis/" + event.getEmoji().getIdLong() + ".webp"));
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void onGenericEmojiUpdate(GenericEmojiUpdateEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null) return;
        EmoteFile ef = POJOUtils.parse(event.getEmoji());
        writeFile(server.gitPath().resolve(EmoteFile.FILE_PATH.formatted(event.getEmoji().getIdLong())), ef);
        if (ef.url != null)
            AssetDownloader.download(ef.url,
                server.gitPath().resolve("emojis/" + event.getEmoji().getIdLong() + ".webp"));
    }

    @Override
    public void onEmojiRemoved(EmojiRemovedEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null) return;
        deleteFile(server.gitPath().resolve(EmoteFile.FILE_PATH.formatted(event.getEmoji().getIdLong())));
    }

    // ── Stickers ──────────────────────────────────────────────────────────────

    @Override
    public void onGuildStickerAdded(GuildStickerAddedEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null) return;
        StickerFile sf = POJOUtils.parse(event.getSticker());
        writeFile(server.gitPath().resolve(StickerFile.FILE_PATH.formatted(event.getSticker().getIdLong())), sf);
        if (sf.url != null)
            AssetDownloader.download(sf.url,
                server.gitPath().resolve("stickers/" + event.getSticker().getIdLong() + ".webp"));
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void onGenericGuildStickerUpdate(GenericGuildStickerUpdateEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null) return;
        StickerFile sf = POJOUtils.parse(event.getSticker());
        writeFile(server.gitPath().resolve(StickerFile.FILE_PATH.formatted(event.getSticker().getIdLong())), sf);
        if (sf.url != null)
            AssetDownloader.download(sf.url,
                server.gitPath().resolve("stickers/" + event.getSticker().getIdLong() + ".webp"));
    }

    @Override
    public void onGuildStickerRemoved(GuildStickerRemovedEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null) return;
        deleteFile(server.gitPath().resolve(StickerFile.FILE_PATH.formatted(event.getSticker().getIdLong())));
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
