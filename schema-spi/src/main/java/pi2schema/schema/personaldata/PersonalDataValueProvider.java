package pi2schema.schema.personaldata;

import java.nio.ByteBuffer;

public interface PersonalDataValueProvider<T> {
    ByteBuffer valueFrom(T instance);
}
