package pi2schema.functional;

public interface ThrowingConsumer<T, E extends Exception> {

    void accept(T t) throws E;
}
