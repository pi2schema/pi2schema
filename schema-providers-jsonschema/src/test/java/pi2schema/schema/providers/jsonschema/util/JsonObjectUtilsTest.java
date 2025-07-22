package pi2schema.schema.providers.jsonschema.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonObjectUtilsTest {

    @Test
    void shouldCreateDeepCopyOfSimpleMap() {
        Map<String, Object> original = Map.of("name", "John", "age", 30);

        Map<String, Object> copy = JsonObjectUtils.deepCopy(original);

        assertThat(copy).isEqualTo(original);
        assertThat(copy).isNotSameAs(original);
    }

    @Test
    void shouldCreateDeepCopyOfNestedMap() {
        Map<String, Object> nested = Map.of("email", "john@example.com");
        Map<String, Object> original = Map.of("name", "John", "profile", nested);

        Map<String, Object> copy = JsonObjectUtils.deepCopy(original);

        assertThat(copy).isEqualTo(original);
        assertThat(copy).isNotSameAs(original);
        assertThat(copy.get("profile")).isNotSameAs(original.get("profile"));
    }

    @Test
    void shouldGetNestedValueWithDotNotation() {
        Map<String, Object> data = Map.of("user", Map.of("profile", Map.of("email", "john@example.com")));

        Object value = JsonObjectUtils.getNestedValue(data, "user.profile.email");

        assertThat(value).isEqualTo("john@example.com");
    }

    @Test
    void shouldReturnNullForNonExistentNestedPath() {
        Map<String, Object> data = Map.of("name", "John");

        Object value = JsonObjectUtils.getNestedValue(data, "user.profile.email");

        assertThat(value).isNull();
    }

    @Test
    void shouldSetNestedValueWithDotNotation() {
        Map<String, Object> data = new HashMap<>();

        JsonObjectUtils.setNestedValue(data, "user.profile.email", "john@example.com");

        assertThat(JsonObjectUtils.getNestedValue(data, "user.profile.email")).isEqualTo("john@example.com");
    }

    @Test
    void shouldSetNestedValueInExistingStructure() {
        Map<String, Object> data = new HashMap<>();
        data.put("user", new HashMap<String, Object>());

        JsonObjectUtils.setNestedValue(data, "user.profile.email", "john@example.com");

        assertThat(JsonObjectUtils.getNestedValue(data, "user.profile.email")).isEqualTo("john@example.com");
    }

    @Test
    void shouldOverwriteExistingNestedValue() {
        Map<String, Object> data = new HashMap<>();
        JsonObjectUtils.setNestedValue(data, "user.email", "old@example.com");

        JsonObjectUtils.setNestedValue(data, "user.email", "new@example.com");

        assertThat(JsonObjectUtils.getNestedValue(data, "user.email")).isEqualTo("new@example.com");
    }
}
