package dev.noobth.pulsebackend.controller;

import dev.noobth.pulsebackend.domain.Api;
import dev.noobth.pulsebackend.domain.CheckResult;
import dev.noobth.pulsebackend.dto.CreateApiRequestDto;
import dev.noobth.pulsebackend.dto.CreateApiResponseDto;
import dev.noobth.pulsebackend.service.ApiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/apis")
@RequiredArgsConstructor
public class ApiController {

    private final ApiService apiService;

    @PostMapping
    public ResponseEntity<CreateApiResponseDto> registerApi(@RequestBody @Valid CreateApiRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(apiService.registerApi(request));
    }

    @GetMapping
    public ResponseEntity<List<Api>> getAllApis() {
        return ResponseEntity.ok(apiService.getAllApis());
    }

    @GetMapping("/{apiId}")
    public ResponseEntity<Api> getApi(@PathVariable String apiId) {
        return ResponseEntity.ok(apiService.getApi(apiId));
    }

    @DeleteMapping("/{apiId}")
    public ResponseEntity<Void> deleteApi(@PathVariable String apiId) {
        apiService.deleteApi(apiId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{apiId}/enabled")
    public ResponseEntity<Map<String, Boolean>> toggleEnabled(@PathVariable String apiId) {
        boolean enabled = apiService.toggleEnabled(apiId);
        return ResponseEntity.ok(Map.of("enabled", enabled));
    }

    @GetMapping("/{apiId}/results")
    public ResponseEntity<List<CheckResult>> getResults(
            @PathVariable String apiId,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(apiService.getResults(apiId, limit));
    }
}
