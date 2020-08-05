package com.github.gustavomonarin.kafkagdpr.core.personaldata;

public class InvalidEncryptedMessageException extends RuntimeException {

    public InvalidEncryptedMessageException(Throwable cause) {
        super(cause);
    }
}
