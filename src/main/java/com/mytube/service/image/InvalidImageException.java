package com.mytube.service.image;

/** Thrown when an uploaded file cannot be decoded as an image. */
public class InvalidImageException extends RuntimeException {
    public InvalidImageException(String message) {
        super(message);
    }
}
