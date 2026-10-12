package com.flowyoga.course.client;

public class MemberServiceCallException extends RuntimeException {

    public MemberServiceCallException(String message) {
        super(message);
    }

    public MemberServiceCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
