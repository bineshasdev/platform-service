package com.flowbinary.platformservice.exception;

import com.flowbinary.platformservice.model.FileType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
public class FileTypeNotAllowedException extends RuntimeException {

    public FileTypeNotAllowedException(String mimeType, FileType fileType) {
        super(String.format(
            "Content-Type '%s' is not allowed for file type %s. Allowed: %s",
            mimeType, fileType.name(), fileType.allowedMimeTypes()));
    }

    public FileTypeNotAllowedException(long sizeBytes, long maxBytes, FileType fileType) {
        super(String.format(
            "File size %d bytes exceeds the %d byte limit for type %s",
            sizeBytes, maxBytes, fileType.name()));
    }
}
