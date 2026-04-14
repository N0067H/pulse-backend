package dev.noobth.pulsebackend.controller;

import dev.noobth.pulsebackend.service.ApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/apis")
@RequiredArgsConstructor
public class ApiController {

    private final ApiService apiService;

    @PostMapping
    public ResponseEntity<?> registerApi() {
        return null;
    }

    @GetMapping
    public ResponseEntity<?> getAllApis() {
        return null;
    }

    @GetMapping("/{apiId}")
    public ResponseEntity<?> getApi(@PathVariable String apiId) {
        return null;
    }

    @DeleteMapping("/{apiId}")
    public ResponseEntity<?> deleteApi(@PathVariable String apiId) {
        return null;
    }

    @PatchMapping("/{apiId}/enabled")
    public ResponseEntity<?> toggleEnabled(@PathVariable String apiId) {
        return null;
    }
}
