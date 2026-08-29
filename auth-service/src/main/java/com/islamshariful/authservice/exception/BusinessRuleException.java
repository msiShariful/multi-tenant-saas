package com.islamshariful.authservice.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

/** A syntactically valid request that the current state of the system will not allow. */
public class BusinessRuleException extends ApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BusinessRuleException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
