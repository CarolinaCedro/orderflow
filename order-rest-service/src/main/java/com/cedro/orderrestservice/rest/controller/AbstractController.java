package com.cedro.orderrestservice.rest.controller;

import com.cedro.orderrestservice.rest.Rest;
import com.cedro.orderrestservice.rest.service.impl.AbstractService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

public abstract class AbstractController<Req, Res> implements Rest<Req, Res> {

    protected abstract AbstractService<Req, Res, ?> getService();

    @Override
    public ResponseEntity<Res> save(Req value, String returnEntity) {
        return getService().save(value, returnEntity);
    }

    @Override
    public ResponseEntity<Res> update(String id, Req model) {
        return getService().update(id, model);
    }

    @Override
    public void deleteById(String id) {
        getService().deleteById(id);
    }

    @Override
    public ResponseEntity<Res> findById(String id) {
        return getService().findById(id);
    }

    @Override
    public ResponseEntity<List<Res>> list(Map<String, String> allRequestParams) {
        return getService().list(allRequestParams);
    }

    @Override
    public ResponseEntity<Page<Res>> listPage(Map<String, String> allRequestParams, Pageable pageable) {
        return getService().listPage(allRequestParams, pageable);
    }

    @Override
    public ResponseEntity<Long> count(Map<String, String> allRequestParams) {
        return getService().count(allRequestParams);
    }
}
