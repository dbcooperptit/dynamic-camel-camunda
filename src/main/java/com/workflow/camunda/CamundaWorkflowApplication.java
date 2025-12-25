package com.workflow.camunda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Camunda BPM Workflow Application
 * 
 * Main entry point for the Spring Boot application with Camunda integration.
 * 
 * Web UIs accessible at:
 * - Camunda Cockpit: http://localhost:8080/camunda/app/cockpit
 * - Camunda Tasklist: http://localhost:8080/camunda/app/tasklist
 * - Camunda Admin: http://localhost:8080/camunda/app/admin
 * - H2 Console: http://localhost:8080/h2-console
 * 
 * Login credentials (default):
 * - Username: admin
 * - Password: admin
 */
@SpringBootApplication
public class CamundaWorkflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(CamundaWorkflowApplication.class, args);
    }
}
