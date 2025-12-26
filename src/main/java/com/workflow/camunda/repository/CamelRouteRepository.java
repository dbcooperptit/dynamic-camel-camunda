package com.workflow.camunda.repository;

import com.workflow.camunda.entity.CamelRouteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CamelRouteRepository extends JpaRepository<CamelRouteEntity, String> {

	List<CamelRouteEntity> findAllByTenantId(String tenantId);

	Optional<CamelRouteEntity> findByIdAndTenantId(String id, String tenantId);

	void deleteByIdAndTenantId(String id, String tenantId);
}
