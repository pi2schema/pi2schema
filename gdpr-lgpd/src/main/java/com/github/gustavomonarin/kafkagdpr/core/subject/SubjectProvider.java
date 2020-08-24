package com.github.gustavomonarin.kafkagdpr.core.subject;

public interface SubjectProvider<T> {

    String subjectFrom(T instance);

}
