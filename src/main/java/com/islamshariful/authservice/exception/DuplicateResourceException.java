package com.islamshariful.authservice.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends ApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DuplicateResourceException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
