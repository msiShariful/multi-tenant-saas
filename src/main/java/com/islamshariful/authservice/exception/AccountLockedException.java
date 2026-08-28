package com.islamshariful.authservice.exception;

import java.io.Serial;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/** Temporary lockout after repeated failed logins. 423 rather than 401: the credentials are not the issue. */
public class AccountLockedException extends ApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Instant lockedUntil;

    public AccountLockedException(Instant lockedUntil) {
        super(HttpStatus.LOCKED, "ACCOUNT_LOCKED", "Account is temporarily locked after repeated failed logins");
        this.lockedUntil = lockedUntil;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}
