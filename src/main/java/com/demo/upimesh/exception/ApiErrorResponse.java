package com.demo.upimesh.exception;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonFormat;

public record ApiErrorResponse(@JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp, int status, String errorCode,
                               String message, String path, String traceId) {}
