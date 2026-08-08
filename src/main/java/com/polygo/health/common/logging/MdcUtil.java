package com.polygo.health.common.logging;

import com.polygo.health.common.constants.HeaderNames;
import com.polygo.health.common.constants.LogKeys;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.UUID;

public final class MdcUtil {

    private MdcUtil() {}

    public static String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    public static String generateJobCorrelationId(String jobName) {
        return String.format("job-%s-%s", jobName, UUID.randomUUID().toString().substring(0, 8));
    }

    public static String getOrCreateCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(HeaderNames.X_CORRELATION_ID);
        return StringUtils.hasText(correlationId) ? correlationId : generateCorrelationId();
    }

    public static void setCorrelationId(String correlationId) {
        MDC.put(LogKeys.CORRELATION_ID, correlationId);
    }

    public static String getCorrelationId() {
        return MDC.get(LogKeys.CORRELATION_ID);
    }

    public static void setRequestContext(String method, String path) {
        MDC.put(LogKeys.METHOD, method);
        MDC.put(LogKeys.PATH, path);
    }

    public static void setStatus(int status) {
        MDC.put(LogKeys.STATUS, String.valueOf(status));
    }

    public static void setDuration(long durationMs) {
        MDC.put(LogKeys.DURATION_MS, String.valueOf(durationMs));
    }

    public static void setJobName(String jobName) {
        MDC.put(LogKeys.JOB_NAME, jobName);
    }

    public static void setService(String service) {
        MDC.put(LogKeys.SERVICE, service);
    }

    public static String getService() {
        return MDC.get(LogKeys.SERVICE);
    }

    public static void setErrorCode(String errorCode) {
        MDC.put(LogKeys.ERROR_CODE, errorCode);
    }

    public static void setErrorMessage(String errorMessage) {
        MDC.put(LogKeys.ERROR_MESSAGE, errorMessage);
    }

    public static void clear() {
        MDC.clear();
    }

    public static void setProvider(String provider) {
        MDC.put(LogKeys.PROVIDER, provider);
    }
}
