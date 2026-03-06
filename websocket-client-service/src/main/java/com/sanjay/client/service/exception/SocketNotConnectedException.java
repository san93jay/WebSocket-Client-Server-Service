package com.sanjay.client.service.exception;

public class SocketNotConnectedException extends RuntimeException {
    public SocketNotConnectedException(String message) {
        super(message);
    }
}
