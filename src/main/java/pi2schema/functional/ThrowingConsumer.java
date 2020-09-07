package pi2schema.functional;

import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface ThrowingConsumer<T, E extends Exception> {

    void accept(@NotNull T t) throws E;
}
