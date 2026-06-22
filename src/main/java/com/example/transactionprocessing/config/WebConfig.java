package com.example.transactionprocessing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolverCustomizer;

/**
 * Caps page size globally so a caller can't request, say, 50,000 rows in a single GET
 * /transactions call and put unexpected load on the database — individual @PageableDefault
 * annotations on controller methods set sensible per-endpoint defaults, this just enforces the
 * ceiling underneath all of them.
 */
@Configuration
public class WebConfig {

    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer() {
        return resolver -> {
            resolver.setMaxPageSize(100);
            resolver.setFallbackPageable(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));
        };
    }
}
