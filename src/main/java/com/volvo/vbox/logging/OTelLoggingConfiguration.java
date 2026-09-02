package com.volvo.vbox.core.logging;

import io.opentelemetry.api.trace.Tracer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OTelLoggingConfiguration {

    @Bean
    @ConditionalOnMissingBean(OTelStructuredLogger.class)
    public OTelStructuredLogger otelStructuredLogger(Tracer tracer) {
        return new OTelStructuredLogger(tracer);
    }

}