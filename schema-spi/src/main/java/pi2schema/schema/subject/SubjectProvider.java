package pi2schema.schema.subject;

public interface SubjectProvider<T> {

    String subjectFrom(T instance);

}
