package com.cedro.orderexception.infra.model;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class ErrorView {

    private LocalDateTime timestamp;
    private String msg;
    private HttpStatus status;
    private String error;
    private String path;
    private Class aClass;

}
