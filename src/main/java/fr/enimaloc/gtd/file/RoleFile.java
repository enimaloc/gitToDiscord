package fr.enimaloc.gtd.file;

import java.util.HashMap;
import java.util.Map;

public class RoleFile {
    public static final String FILE_PATH = "roles/%d.toml";

    public long id;
    public String name;
    public Map<String, Boolean> permissions = new HashMap<>();
    public String iconUrl;
    public int primaryColor;
    public int secondaryColor;
    public int tertiaryColor;
    public boolean mentionable;
    public boolean hoist;
    public int position;
    public boolean managed;
    public boolean publicRole;
    public long botId;
    public boolean isBot;
    public boolean isBoost;
    public boolean isIntegration;
    public boolean isLinkedRole;
}
