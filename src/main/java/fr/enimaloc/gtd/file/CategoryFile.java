package fr.enimaloc.gtd.file;

import java.util.HashMap;
import java.util.Map;

public class CategoryFile {
    public static final String FILE_PATH = "categories/%d.toml";

    public long id;
    public String type = "category";
    public String name;
    public int position;
    public Map<Long, Map<String, Integer>> memberOverride = new HashMap<>();
    public Map<Long, Map<String, Integer>> roleOverride = new HashMap<>();
}
