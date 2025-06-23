package com.cedro.orderrestservice.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

public interface Rest<T> {
    @PostMapping
    ResponseEntity<T> save(@RequestBody T value, @RequestParam(required = false, value = "returnEntity", defaultValue = "") String returnEntity);

    @PutMapping("/{id}")
    ResponseEntity<T> update(@PathVariable String id, @RequestBody T model);

    @DeleteMapping("/{id}")
    void deleteById(@PathVariable String id);

    @GetMapping("/{id}")
    ResponseEntity<T> findById(@PathVariable String id);

    @GetMapping
    ResponseEntity<List<T>> list(@RequestParam Map<String, String> allRequestParams);

    @GetMapping("/count")
    ResponseEntity<Long> count(@RequestParam Map<String, String> allRequestParams);
}

