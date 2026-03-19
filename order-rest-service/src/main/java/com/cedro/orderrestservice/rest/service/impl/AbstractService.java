package com.cedro.orderrestservice.rest.service.impl;

import com.cedro.orderrestservice.rest.Rest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractService<Req, Res, Entity> implements Rest<Req, Res> {

    private static final Set<String> RESERVED_PARAMS = Set.of("page", "size", "sort", "returnEntity");

    @Autowired
    private MongoTemplate mongoTemplate;

    protected abstract MongoRepository<Entity, String> getRepository();

    protected abstract Class<Entity> getEntityClass();

    protected abstract Entity toEntity(Req request);

    protected abstract Entity updateEntity(Entity existing, Req request);

    protected abstract Res toResponse(Entity entity);

    @Override
    public ResponseEntity<Res> save(Req request, String returnEntity) {
        Entity saved = getRepository().save(toEntity(request));
        return ResponseEntity.ok(toResponse(saved));
    }

    @Override
    public ResponseEntity<Res> update(String id, Req request) {
        return getRepository().findById(id)
                .map(existing -> ResponseEntity.ok(toResponse(getRepository().save(updateEntity(existing, request)))))
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public void deleteById(String id) {
        getRepository().deleteById(id);
    }

    @Override
    public ResponseEntity<Res> findById(String id) {
        return getRepository().findById(id)
                .map(entity -> ResponseEntity.ok(toResponse(entity)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<List<Res>> list(Map<String, String> params) {
        List<Res> results = mongoTemplate.find(buildQuery(params), getEntityClass())
                .stream().map(this::toResponse).toList();
        if (results.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(results);
    }

    @Override
    public ResponseEntity<Page<Res>> listPage(Map<String, String> params, Pageable pageable) {
        Query query = buildQuery(params);
        long total = mongoTemplate.count(query, getEntityClass());
        query.with(pageable);
        List<Res> content = mongoTemplate.find(query, getEntityClass())
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(new PageImpl<>(content, pageable, total));
    }

    @Override
    public ResponseEntity<Long> count(Map<String, String> params) {
        return ResponseEntity.ok(mongoTemplate.count(buildQuery(params), getEntityClass()));
    }

    private Query buildQuery(Map<String, String> params) {
        Query query = new Query(Criteria.where("metadata.deleted").ne(true));
        params.entrySet().stream()
                .filter(e -> !RESERVED_PARAMS.contains(e.getKey()))
                .forEach(e -> query.addCriteria(Criteria.where(e.getKey()).is(e.getValue())));
        return query;
    }
}
