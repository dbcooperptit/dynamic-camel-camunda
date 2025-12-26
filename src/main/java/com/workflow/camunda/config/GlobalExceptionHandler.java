package com.workflow.camunda.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.io.IOException;

/**
 * Global exception handler for SSE and other web exceptions
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Handle IOException from SSE connections being closed by client.
     * This is a normal occurrence when:
     * - Browser tab is closed
     * - Page is refreshed
     * - Network connection is lost
     * - Client navigates away
     * 
     * We log at debug level to reduce noise in logs while still tracking for debugging.
     */
    @ExceptionHandler(IOException.class)
    public void handleIOException(IOException ex) {
        String message = ex.getMessage();
        
        // Check if this is a common SSE connection closed error
        if (message != null && (
            message.contains("An established connection was aborted") ||
            message.contains("Connection reset by peer") ||
            message.contains("Broken pipe") ||
            message.contains("Connection closed"))) {
            
            // This is expected behavior - client disconnected
            log.debug("SSE connection closed by client: {}", message);
        } else {
            // Unexpected IOException - log at warn level
            log.warn("Unexpected IOException: {}", message, ex);
        }
    }
}
