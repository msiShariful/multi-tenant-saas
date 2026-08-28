package com.islamshariful.authservice.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

/**
 * Base class for failures that are part of the API contract.
 *
 * <p>Each carries the status it should produce and a stable machine-readable {@code code}. Clients branch
 * on the code, never on the human-readable detail — the detail is free to change wording, the code is not.
 */
public abstract class ApiException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
