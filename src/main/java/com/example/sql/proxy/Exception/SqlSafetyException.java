package com.example.sql.proxy.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class SqlSafetyException extends RuntimeException {

    public SqlSafetyException(String message) {
        super(message);
    }
}
