package fr.enimaloc.gtd.file;

import java.util.ArrayList;
import java.util.List;

public class EmoteFile {
    public static final String FILE_PATH = "emojis/%d.toml";

    public long id;
    public long owner;
    public boolean animated;
    public boolean available;
    public boolean managed;
    public String name;
    public String url;
    public List<Long> allowedRoleIds = new ArrayList<>();
}
