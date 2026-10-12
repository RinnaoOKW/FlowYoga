package com.flowyoga.course.web;

import com.flowyoga.course.client.MemberServiceCallException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CourseApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CourseApiExceptionHandler.class);

    @ExceptionHandler(MemberServiceCallException.class)
    public ResponseEntity<ApiErrorResponse> memberUnavailable(MemberServiceCallException exception) {
        log.warn("member service call failed: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiErrorResponse("MEMBER_SERVICE_UNAVAILABLE", "会员卡服务调用失败，本次不视为成功"));
    }

    public record ApiErrorResponse(String code, String message) {
    }
}
