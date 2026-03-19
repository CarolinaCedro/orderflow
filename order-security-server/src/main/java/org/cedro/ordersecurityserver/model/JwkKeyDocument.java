package org.cedro.ordersecurityserver.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "jwk_keys")
public class JwkKeyDocument {

    @Id
    private String id;

    private String publicKey;

    private String privateKey;
}
