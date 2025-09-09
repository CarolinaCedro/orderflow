package com.cedro.orderexception.infra.exception;

import com.cedro.orderexception.infra.model.ErrorView;

public class ResourceNotFoundException extends RuntimeException {

    private final String error;
    private final String path;

    public ResourceNotFoundException(ErrorView errorView) {
        super(errorView.getMsg());
        this.error = errorView.getError();
        this.path = errorView.getPath();
    }

}
