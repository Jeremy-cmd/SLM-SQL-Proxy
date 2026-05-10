package com.example.sql.proxy.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ExceptionHandler {

    @org.springframework.web.bind.annotation.ExceptionHandler(SqlSafetyException.class)
    public ResponseEntity<ErrorResponse> handleSecurity(SqlSafetyException e) {
        ErrorResponse errorResponse = new ErrorResponse(e.getMessage(), "Forbidden SQL");
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        ErrorResponse errorResponse = new ErrorResponse("An unexpected error occured", "Internal Error");
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
