package com.github.gustavomonarin.kafkagdpr.core.personaldata;

public interface PersonalDataValueProvider<T> {

    byte[] valueFrom(T instance);

}
