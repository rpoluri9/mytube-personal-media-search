package com.mytube.service.web;

/** Thrown when a Google web API call (Vision / YouTube) fails. */
public class WebSearchException extends RuntimeException {
    public WebSearchException(String message) {
        super(message);
    }

    public WebSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
