package fr.enimaloc.gtd.file;

import java.util.Set;

public class StickerFile {
    public static final String FILE_PATH = "stickers/%d.toml";

    public long id;
    public long owner;
    public boolean available;
    public String description;
    public Set<String> tags;
    public String name;
    public String url;
}
