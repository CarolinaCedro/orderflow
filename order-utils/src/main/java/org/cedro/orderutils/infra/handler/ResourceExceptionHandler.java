package org.cedro.orderutils.infra.handler;

import org.cedro.orderutils.infra.exception.ResourceNotFoundException;
import org.cedro.orderutils.infra.model.ErrorView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
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

    @ExceptionHandler
    private ResponseEntity<ErrorView> handleAccessDeniedException(AccessDeniedException error) {
        ErrorView errorView = new ErrorView(
                LocalDateTime.now(),
                "Access denied",
                HttpStatus.FORBIDDEN,
                "Forbidden",
                error.getLocalizedMessage(),
                error.getClass()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorView);
    }

    @ExceptionHandler
    private ResponseEntity<ErrorView> handleAuthenticationException(AuthenticationException error) {
        ErrorView errorView = new ErrorView(
                LocalDateTime.now(),
                "Authentication required",
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                error.getLocalizedMessage(),
                error.getClass()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorView);
    }
}
