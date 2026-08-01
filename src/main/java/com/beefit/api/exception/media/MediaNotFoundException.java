package com.beefit.api.exception.media;

public class MediaNotFoundException extends RuntimeException {

    public MediaNotFoundException() {
        super("Mídia não encontrada.");
    }
}
