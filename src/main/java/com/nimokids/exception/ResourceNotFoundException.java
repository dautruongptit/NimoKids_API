package com.nimokids.exception;

/** Generic "entity does not exist" failure (topic, game mode, sticker, ...). */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resource, Object id) {
        super(ErrorCode.RESOURCE_NOT_FOUND, resource + " not found: " + id);
    }
}
