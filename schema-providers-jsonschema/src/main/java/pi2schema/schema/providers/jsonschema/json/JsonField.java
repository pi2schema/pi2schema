package pi2schema.schema.providers.jsonschema.json;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map.Entry;

public record JsonField(String name, JsonNode node, JsonNode parent, String path) {
    public JsonField(String key, JsonNode node, JsonNode root) {
        this(key, node, root, "");
    }

    public String absolutPath() {
        if (path.isEmpty()) {
            return name;
        }
        return path + "." + name;
    }

    public JsonField child(Entry<String, JsonNode> child) {
        return new JsonField(child.getKey(), child.getValue(), node, absolutPath());
    }
}
