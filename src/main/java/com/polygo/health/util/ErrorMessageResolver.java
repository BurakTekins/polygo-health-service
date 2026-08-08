package com.polygo.health.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

public final class ErrorMessageResolver {

    private ErrorMessageResolver() {
    }

    public static String resolve(Exception e) {

        if (e instanceof HttpStatusCodeException ex) {
            int code = ex.getStatusCode().value();
            HttpStatus status = HttpStatus.resolve(code);
            return status != null
                    ? code + " " + status.getReasonPhrase()
                    : "HTTP " + code;
        }

        if (e instanceof ResourceAccessException) {
            return "connection error";
        }

        String msg = e.getMessage();
        if (msg == null) {
            return "unexpected error";
        }

        if (msg.contains("502")) {
            return "502 Bad Gateway";
        }

        if (msg.contains("timed out")) {
            return "timeout";
        }

        if (msg.contains("Connection refused")) {
            return "connection refused";
        }

        return "unexpected error";
    }
}
