package com.flowyoga.user.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthApiExceptionHandler {
    @ExceptionHandler(AuthException.class)
    ResponseEntity<ErrorView> authError(AuthException exception) {
        return ResponseEntity.status(exception.status()).body(new ErrorView(exception.code(), exception.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ErrorView> invalidRequest(Exception exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorView("INVALID_REQUEST", "请检查账号、密码和登录身份的填写格式"));
    }
    public record ErrorView(String code, String message) {}
}
