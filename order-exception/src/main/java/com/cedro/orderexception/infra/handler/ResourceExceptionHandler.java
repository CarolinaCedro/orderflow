package com.cedro.orderexception.infra.handler;

import com.cedro.orderexception.infra.exception.ResourceNotFoundException;
import com.cedro.orderexception.infra.model.ErrorView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;

@ControllerAdvice
public class ResourceExceptionHandler extends ResponseEntityExceptionHandler {


    @ExceptionHandler
    private ResponseEntity<ErrorView> handleResourceNotFoundException(ResourceNotFoundException error) {
        ErrorView errorView = new ErrorView(
                LocalDateTime.now(),
                "Resource not found",
                HttpStatus.NOT_FOUND,
                "Not Found",
                error.getLocalizedMessage(),
                error.getClass()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorView);
    }


}
