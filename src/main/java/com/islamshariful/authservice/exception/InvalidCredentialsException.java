package com.islamshariful.authservice.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

/**
 * Thrown for every failed login reason that must stay indistinguishable to the caller: unknown tenant,
 * unknown email, wrong password.
 *
 * <p>Returning "no such user" versus "wrong password" turns the login endpoint into an account-enumeration
 * oracle. One message, one status, for all three.
 */
public class InvalidCredentialsException extends ApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid tenant, email or password");
    }
}
