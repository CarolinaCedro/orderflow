package org.cedro.ordersecurityserver.repository;

import org.cedro.ordersecurityserver.model.JwkKeyDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface JwkKeyRepository extends MongoRepository<JwkKeyDocument, String> {
}
