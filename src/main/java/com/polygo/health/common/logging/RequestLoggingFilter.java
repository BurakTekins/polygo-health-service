package com.polygo.health.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String path = request.getRequestURI();

        MdcUtil.setRequestContext(method, path);
        log.info("Request started: {} {}", method, path);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();

            MdcUtil.setStatus(status);
            MdcUtil.setDuration(duration);

            if (status >= 500) {
                log.error("Request finished: {} {} status={} durationMs={}", method, path, status, duration);
            } else if (status >= 400) {
                log.warn("Request finished: {} {} status={} durationMs={}", method, path, status, duration);
            } else {
                log.info("Request finished: {} {} status={} durationMs={}", method, path, status, duration);
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health");
    }
}