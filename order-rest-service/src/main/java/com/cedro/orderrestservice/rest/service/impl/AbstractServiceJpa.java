package com.cedro.orderrestservice.rest.service.impl;

import com.cedro.orderrestservice.rest.Rest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

public abstract class AbstractServiceJpa<T> implements Rest<T> {

    protected abstract JpaRepository<T, String> getRepository();

    @Override
    public ResponseEntity<T> save(T value, String returnEntity) {
        return ResponseEntity.ok(getRepository().save(value));
    }

    @Override
    public ResponseEntity<T> update(String id, T model) {
        return ResponseEntity.ok(getRepository().findById(id).orElse(null));
    }

    @Override
    public void deleteById(String id) {
        getRepository().deleteById(id);
    }

    @Override
    public ResponseEntity<T> findById(String id) {
        return getRepository().findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }


    @Override
    public ResponseEntity<List<T>> list(Map<String, String> allRequestParams) {
        List<T> results = getRepository().findAll();

        if (results.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(results);
    }


    @Override
    public ResponseEntity<Long> count(Map<String, String> allRequestParams) {
        long total = getRepository().count(); // Conta os registros no banco
        return ResponseEntity.ok(total);
    }


}
