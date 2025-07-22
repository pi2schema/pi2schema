package pi2schema.schema.providers.jsonschema.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for working with JSON objects represented as Map<String, Object>.
 * Provides functionality for deep copying and nested field access.
 */
public class JsonObjectUtils {

    /**
     * Creates a deep copy of a JSON object represented as Map<String, Object>.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> deepCopy(Map<String, Object> original) {
        if (original == null) {
            return null;
        }

        Map<String, Object> copy = new HashMap<>();
        for (Map.Entry<String, Object> entry : original.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                copy.put(entry.getKey(), deepCopy((Map<String, Object>) value));
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }

    /**
     * Gets a nested value from a map using dot notation (e.g., "user.profile.email").
     */
    public static Object getNestedValue(Map<String, Object> map, String path) {
        if (map == null || path == null) {
            return null;
        }

        String[] pathParts = path.split("\\.");
        Object current = map;

        for (String part : pathParts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }

        return current;
    }

    /**
     * Sets a nested value in a map using dot notation (e.g., "user.profile.email").
     * Creates intermediate maps as needed.
     */
    @SuppressWarnings("unchecked")
    public static void setNestedValue(Map<String, Object> map, String path, Object value) {
        if (map == null || path == null) {
            return;
        }

        String[] pathParts = path.split("\\.");
        Map<String, Object> current = map;

        // Navigate to the parent of the target field
        for (int i = 0; i < pathParts.length - 1; i++) {
            String part = pathParts[i];
            Object next = current.get(part);

            if (!(next instanceof Map)) {
                // Create intermediate map if it doesn't exist
                next = new HashMap<String, Object>();
                current.put(part, next);
            }

            current = (Map<String, Object>) next;
        }

        // Set the final value
        String finalKey = pathParts[pathParts.length - 1];
        current.put(finalKey, value);
    }
}
