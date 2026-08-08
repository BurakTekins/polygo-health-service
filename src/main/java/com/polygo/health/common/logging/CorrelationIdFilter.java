package com.polygo.health.common.logging;

import com.polygo.health.common.constants.HeaderNames;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class CorrelationIdFilter extends OncePerRequestFilter {

    private final String serviceName;

    public CorrelationIdFilter(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = MdcUtil.getOrCreateCorrelationId(request);
        MdcUtil.setCorrelationId(correlationId);

        if (serviceName != null) {
            MdcUtil.setService(serviceName);
        }

        response.addHeader(HeaderNames.X_CORRELATION_ID, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MdcUtil.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health");
    }
}