package pi2schema.schema.personaldata;

public interface PersonalDataValueProvider<T> {

    byte[] valueFrom(T instance);
}
