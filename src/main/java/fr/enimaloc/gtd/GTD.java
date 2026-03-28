package fr.enimaloc.gtd;

import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import fr.enimaloc.gtd.archive.MessageArchiver;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class GTD extends ListenerAdapter {
    public static final TomlMapper MAPPER = new TomlMapper();
    private final JDA jda;
    private final Config config;
    private final Map<Long, Server> servers = new HashMap<>();

    static void main(String... args) throws IOException {
        String tomlPath = System.getenv().getOrDefault("GTD_CONFIG", "./config.toml");
        if (!Files.exists(new File(tomlPath).toPath())) {
            MAPPER.writer().writeValue(new File(tomlPath), new Config());
        }
        GTD gtd = new GTD(MAPPER.readValue(new File(tomlPath), Config.class));
        gtd.init();
    }

    public GTD(Config config) {
        this(JDABuilder.createDefault(config.botToken).build(), config);
    }

    public GTD(JDA jda, Config config) {
        this.jda = jda;
        this.config = config;
    }

    public void init() {
        jda.addEventListener(this);
        jda.addEventListener(new DiscordEventSync(servers));
        List<Command> commands = jda.retrieveCommands().complete();
        Set<String> existing = new HashSet<>();
        for (Command command : commands) {
            existing.add(command.getFullCommandName());
        }

        if (!existing.contains("init")) {
            jda.upsertCommand("init", "Initialize the git repository")
                    .addOption(OptionType.STRING, "repository", "Repository URL", true)
                    .queue();
        }
        if (!existing.contains("push")) {
            jda.upsertCommand("push", "Push changes to the git repository")
                    .queue();
        }
        if (!existing.contains("pull")) {
            jda.upsertCommand("pull", "Pull changes from the git repository")
                    .queue();
        }
        if (!existing.contains("archive")) {
            jda.upsertCommand("archive", "Archive l'historique complet des messages du serveur")
                    .queue();
        }
    }

    public static class Config {
        public String dataPath = "./data";
        public String botToken = System.getenv("DISCORD_TOKEN");
    }

    @Override
    public void onGuildReady(GuildReadyEvent event) {
        super.onGuildReady(event);

        for (Guild guild : jda.getGuilds()) {
            try {
                System.out.println("Initializing server for guild: " + guild.getName());
                servers.put(guild.getIdLong(), new Server(config, guild));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        super.onSlashCommandInteraction(event);
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null) {
            return;
        }
        if ("init".equals(event.getFullCommandName())) {
            event.deferReply(true).queue();
            try {
                server.initGit(event.getOption("repository").getAsString(), event.getGuild());
                event.getHook().editOriginal("Git initialisé + état Discord exporté").queue();
            } catch (Exception e) {
                e.printStackTrace();
                event.getHook().editOriginal("Échec de l'init : " + e.getMessage()).queue();
            }
        } else if ("push".equals(event.getFullCommandName())) {
            event.deferReply(true).queue();
            try {
                server.synchroAll(event.getGuild());
                event.getHook().editOriginal("État Discord exporté dans les fichiers").queue();
            } catch (IOException e) {
                e.printStackTrace();
                event.getHook().editOriginal("Échec du push : " + e.getMessage()).queue();
            }
        } else if ("pull".equals(event.getFullCommandName())) {
            event.deferReply(true).queue();
            try {
                Server.PullResult result = server.pull(event.getGuild());
                event.getHook().editOriginal("Pull terminé : " + result).queue();
            } catch (Exception e) {
                e.printStackTrace();
                event.getHook().editOriginal("Échec du pull : " + e.getMessage()).queue();
            }
        } else if ("archive".equals(event.getFullCommandName())) {
            event.deferReply(true).queue();
            try {
                MessageArchiver archiver = new MessageArchiver(server.gitPath());
                Map<String, Integer> counts = archiver.archive(event.getGuild());
                StringBuilder sb = new StringBuilder("Archive terminée :\n");
                counts.forEach((name, count) -> sb.append("• ").append(name).append(" : ").append(count).append(" messages\n"));
                String response = sb.toString();
                if (response.length() > 1900) {
                    int total = counts.values().stream().mapToInt(Integer::intValue).sum();
                    response = "Archive terminée : " + counts.size() + " channels, " + total + " messages au total.";
                }
                event.getHook().editOriginal(response).queue();
            } catch (Exception e) {
                e.printStackTrace();
                event.getHook().editOriginal("Échec de l'archive : " + e.getMessage()).queue();
            }
        }
    }
}
