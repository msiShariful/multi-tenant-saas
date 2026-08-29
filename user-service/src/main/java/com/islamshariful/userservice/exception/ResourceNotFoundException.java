package com.islamshariful.userservice.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
    }

    public static ResourceNotFoundException of(String resource, Object identifier) {
        return new ResourceNotFoundException("%s '%s' was not found".formatted(resource, identifier));
    }
}
