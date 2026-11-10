package com.example.sql.proxy.Exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class ExceptionHandler {

    @org.springframework.web.bind.annotation.ExceptionHandler(SqlSafetyException.class)
    public ResponseEntity<ErrorResponse> handleSecurity(SqlSafetyException e) {
        ErrorResponse errorResponse = new ErrorResponse(e.getMessage(), "Forbidden SQL");
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(EmptyRequestException.class)
    public ResponseEntity<ErrorResponse> handleRequest(EmptyRequestException e) {
        ErrorResponse errorResponse = new ErrorResponse(e.getMessage(), "Order Request cannot have any empty fields");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(ItemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleItemNotFound(ItemNotFoundException e) {
        ErrorResponse errorResponse = new ErrorResponse(e.getMessage(), "Item Not Found");
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unhandled exception", e);
        ErrorResponse errorResponse = new ErrorResponse("An unexpected error occurred", "Internal Error");
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
