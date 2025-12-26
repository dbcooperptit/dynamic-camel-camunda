package com.workflow.camunda.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "camel_routes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CamelRouteEntity {

    @Id
    @Column(name = "id", nullable = false, length = 128)
    private String id;

    @Column(name = "name")
    private String name;

    @Column(name = "tenant_id", length = 128)
    private String tenantId;

    @Column(name = "description", length = 2000)
    private String description;

    @Lob
    @Column(name = "definition_json", nullable = false)
    private String definitionJson;

    @Column(name = "status", length = 32)
    private String status;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
