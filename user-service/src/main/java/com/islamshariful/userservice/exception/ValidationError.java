package com.islamshariful.userservice.exception;

/** One field-level rejection, surfaced in the {@code errors} member of a problem response. */
public record ValidationError(String field, String message) {}
