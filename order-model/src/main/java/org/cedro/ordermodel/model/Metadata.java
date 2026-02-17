package org.cedro.ordermodel.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Metadata {

    private String createdBy;
    private LocalDateTime createdAt;

    private String updatedBy;
    private LocalDateTime updatedAt;

    private String deletedBy;
    private LocalDateTime deletedAt;

    private Boolean deleted = false;

    private String correlationId;
    private String tenantId;      // multiempresa

    private Long version; // controle otimista

}
