package com.iras;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> applicationError(ApiException error) {
        return ResponseEntity.status(error.status()).body(Map.of(
                "msg", "error", "code", error.status(), "detail", error.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            IllegalArgumentException.class, org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    ResponseEntity<?> invalidInput(Exception error) {
        return applicationError(new ApiException(400, "Invalid request"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> tooLarge(MaxUploadSizeExceededException error) {
        return applicationError(new ApiException(413, "Upload exceeds size limit"));
    }

    @ExceptionHandler(org.springframework.web.ErrorResponseException.class)
    ResponseEntity<?> frameworkError(org.springframework.web.ErrorResponseException error) {
        return applicationError(new ApiException(error.getStatusCode().value(), "Request could not be processed"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpectedError(Exception error) {
        if (error instanceof org.springframework.web.ErrorResponse response)
            return applicationError(new ApiException(response.getStatusCode().value(), "Request could not be processed"));
        return applicationError(new ApiException(500, "Internal service error"));
    }
}