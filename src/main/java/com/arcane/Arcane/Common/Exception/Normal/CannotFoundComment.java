package com.arcane.Arcane.Common.Exception.Normal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class CannotFoundComment extends RuntimeException {
    public CannotFoundComment(String message) {
        super(message);
    }
}
