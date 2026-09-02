package com.volvo.vbox.core.logging;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OTelStructuredLogger {

    private final Tracer tracer;

    public void logWithContext(String message, Map<String, String> attributes) {
        Span span = tracer.spanBuilder("structured.log").startSpan();
        try (Scope scope = span.makeCurrent()) {
            attributes.forEach(span::setAttribute);
            span.addEvent("log.message", buildEventAttributes(message, attributes));
            log.info("Message: {} | Context: {}", message, attributes);
        } finally {
            span.end();
        }
    }

    public void logWithContext(String message, String key, String value) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(key, value);
        logWithContext(message, attributes);
    }

    public void logError(String message, Throwable exception, Map<String, String> attributes) {
        Span span = tracer.spanBuilder("error.log").startSpan();
        try (Scope scope = span.makeCurrent()) {
            span.recordException(exception);
            span.setAttribute("error.type", exception.getClass().getSimpleName());
            span.setAttribute("error.message", exception.getMessage() != null ? exception.getMessage() : "");
            attributes.forEach(span::setAttribute);
            log.error("Error Message: {} | Exception: {} | Context: {}", message, exception.getClass().getSimpleName(), attributes, exception);
        } finally {
            span.end();
        }
    }

    public void logErrorWithContext(String message, Throwable exception, String key, String value) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(key, value);
        logError(message, exception, attributes);
    }

    public void logDebug(String message, Map<String, String> attributes) {
        if (log.isDebugEnabled()) {
            Span span = tracer.spanBuilder("debug.log").startSpan();
            try (Scope scope = span.makeCurrent()) {
                attributes.forEach(span::setAttribute);
                log.debug("Debug Message: {} | Context: {}", message, attributes);
            } finally {
                span.end();
            }
        }
    }

    public void logWarn(String message, Map<String, String> attributes) {
        Span span = tracer.spanBuilder("warn.log").startSpan();
        try (Scope scope = span.makeCurrent()) {
            attributes.forEach(span::setAttribute);
            span.addEvent("warning", buildEventAttributes(message, attributes));
            log.warn("Warning Message: {} | Context: {}", message, attributes);
        } finally {
            span.end();
        }
    }

    public void logWithTraceId(String message, String traceId, Map<String, String> attributes) {
        Map<String, String> enrichedAttributes = new HashMap<>(attributes);
        enrichedAttributes.put("trace.id", traceId);
        logWithContext(message, enrichedAttributes);
    }

    public void logWithCorrelationId(String message, String correlationId, Map<String, String> attributes) {
        Map<String, String> enrichedAttributes = new HashMap<>(attributes);
        enrichedAttributes.put("correlation.id", correlationId);
        logWithContext(message, enrichedAttributes);
    }

    public void logMetric(String metricName, double value, Map<String, String> attributes) {
        Span span = tracer.spanBuilder("metric.log").startSpan();
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute("metric.name", metricName);
            span.setAttribute("metric.value", value);
            attributes.forEach(span::setAttribute);
            log.info("Metric: {} = {} | Attributes: {}", metricName, value, attributes);
        } finally {
            span.end();
        }
    }

    public void logAudit(String action, String actor, String resource, Map<String, String> attributes) {
        Map<String, String> auditAttributes = new HashMap<>(attributes);
        auditAttributes.put("audit.action", action);
        auditAttributes.put("audit.actor", actor);
        auditAttributes.put("audit.resource", resource);
        auditAttributes.put("audit.timestamp", String.valueOf(System.currentTimeMillis()));
        logWithContext("Audit Event", auditAttributes);
    }

    private Map<String, Object> buildEventAttributes(String message, Map<String, String> contextAttributes) {
        Map<String, Object> eventAttributes = new HashMap<>();
        eventAttributes.put("log.message", message);
        eventAttributes.putAll(contextAttributes);
        eventAttributes.put("timestamp", System.currentTimeMillis());
        return eventAttributes;
    }

}