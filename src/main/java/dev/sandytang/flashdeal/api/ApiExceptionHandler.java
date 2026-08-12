package dev.sandytang.flashdeal.api;

import dev.sandytang.flashdeal.domain.SeckillRejectedException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(SeckillRejectedException.class)
    ResponseEntity<Map<String, Object>> rejected(SeckillRejectedException ex) {
        return ResponseEntity.status(ex.status()).body(Map.of("code", ex.code(), "message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> invalid(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST", "message", ex.getMessage()));
    }
}
