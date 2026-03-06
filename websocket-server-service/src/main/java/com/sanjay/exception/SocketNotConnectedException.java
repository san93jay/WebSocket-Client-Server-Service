package com.sanjay.exception;

public class SocketNotConnectedException extends RuntimeException {
    public SocketNotConnectedException(String message) {
        super(message);
    }
}
