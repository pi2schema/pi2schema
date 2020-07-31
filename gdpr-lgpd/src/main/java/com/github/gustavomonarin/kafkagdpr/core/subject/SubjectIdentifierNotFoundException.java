package com.github.gustavomonarin.kafkagdpr.core.subject;

public class SubjectIdentifierNotFoundException extends RuntimeException {

    private final Class<?> strategy;
    private final String fieldName;

    public SubjectIdentifierNotFoundException(Class<?> strategy, String fieldName) {
        this.strategy = strategy;
        this.fieldName = fieldName;
    }

    @Override
    public String getMessage() {
        return String.format("The strategy %s has not found any possible identifiers for the field %s while exact one is required",
                strategy.getSimpleName(),
                fieldName);
    }
}
