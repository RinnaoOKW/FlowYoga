package com.flowyoga.management;
import static com.flowyoga.management.ManagementModels.*;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
@RestControllerAdvice
public class ManagementApiExceptionHandler {
    @ExceptionHandler(ManagementException.class) ResponseEntity<ApiError> management(ManagementException error) { return ResponseEntity.status(error.status()).body(new ApiError(error.code(),error.getMessage())); }
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ApiError> validation(MethodArgumentNotValidException error) { var detail=error.getBindingResult().getFieldError(); return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST",detail==null?"请检查资料格式":detail.getDefaultMessage())); }
    @ExceptionHandler({ConstraintViolationException.class,HandlerMethodValidationException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class}) ResponseEntity<ApiError> invalid(Exception error) { return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST","请检查资料、状态或分页参数的格式")); }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<ApiError> conflict(Exception error) { return ResponseEntity.status(409).body(new ApiError("DATA_CONFLICT","资料已存在或正在变更，请刷新后重试")); }
}
