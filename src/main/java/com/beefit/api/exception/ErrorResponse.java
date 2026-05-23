package com.beefit.api.exception;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse (
    LocalDateTime timestamp,
    Integer status,
    String error,
    String message,
    List<ErrorCampo> fields,
    String path)
{ }
