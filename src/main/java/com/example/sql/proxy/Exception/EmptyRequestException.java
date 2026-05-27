package com.example.sql.proxy.Exception;

public class EmptyRequestException extends RuntimeException {

    public EmptyRequestException(String message) {
        super(message);
    }
}
