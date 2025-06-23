package com.cedro.orderrestservice.rest.controller;

import com.cedro.orderrestservice.rest.Rest;
import com.cedro.orderrestservice.rest.service.impl.AbstractServiceMongo;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public abstract class AbstractControllerMongo<T> implements Rest<T> {

    protected abstract AbstractServiceMongo<T> getService();

    @Override
    public ResponseEntity<T> save(T value, String returnEntity) {
        return getService().save(value, returnEntity);
    }

    @Override
    public ResponseEntity<T> update(String id, T model) {
        return getService().update(id, model);
    }

    @Override
    public void deleteById(String id) {
         getService().deleteById(id);
    }

    @Override
    public ResponseEntity<T> findById(String id) {
        return getService().findById(id);
    }

    @Override
    public ResponseEntity<List<T>> list(Map<String, String> allRequestParams) {
        return getService().list(allRequestParams);
    }

    @Override
    public ResponseEntity<Long> count(Map<String, String> allRequestParams) {
        return getService().count(allRequestParams);
    }
}
