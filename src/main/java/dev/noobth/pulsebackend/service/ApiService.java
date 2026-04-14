package dev.noobth.pulsebackend.service;

import dev.noobth.pulsebackend.repository.ApiRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApiService {

    private final ApiRepository apiRepository;

    public void registerApi() {
    }

    public void getAllApis() {
    }

    public void getApi(String apiId) {
    }

    public void deleteApi(String apiId) {
    }

    public void toggleEnabled(String apiId) {
    }
}
