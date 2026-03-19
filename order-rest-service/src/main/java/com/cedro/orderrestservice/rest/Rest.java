package com.cedro.orderrestservice.rest;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

public interface Rest<Req, Res> {

    @PostMapping
    ResponseEntity<Res> save(@RequestBody @Valid Req value,
                             @RequestParam(required = false, value = "returnEntity", defaultValue = "") String returnEntity);

    @PutMapping("/{id}")
    ResponseEntity<Res> update(@PathVariable String id, @RequestBody @Valid Req model);

    @DeleteMapping("/{id}")
    void deleteById(@PathVariable String id);

    @GetMapping("/{id}")
    ResponseEntity<Res> findById(@PathVariable String id);

    @GetMapping
    ResponseEntity<List<Res>> list(@RequestParam Map<String, String> allRequestParams);

    @GetMapping("/page")
    ResponseEntity<Page<Res>> listPage(@RequestParam Map<String, String> allRequestParams, Pageable pageable);

    @GetMapping("/count")
    ResponseEntity<Long> count(@RequestParam Map<String, String> allRequestParams);
}
