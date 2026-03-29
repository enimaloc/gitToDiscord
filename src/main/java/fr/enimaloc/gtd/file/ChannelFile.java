package fr.enimaloc.gtd.file;

import java.util.HashMap;
import java.util.Map;

public class ChannelFile {
    public static final String FILE_PATH = "channels/%d.toml";

    public long id;
    public String type;
    public String name;
    public int position;
    public long parentCategoryId;
    public int positionInCategory;
    public Map<Long, Map<String, Integer>> memberOverride = new HashMap<>();
    public Map<Long, Map<String, Integer>> roleOverride = new HashMap<>();
}
