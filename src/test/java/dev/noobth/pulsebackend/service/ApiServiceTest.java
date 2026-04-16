package dev.noobth.pulsebackend.service;

import dev.noobth.pulsebackend.domain.Api;
import dev.noobth.pulsebackend.domain.CheckResult;
import dev.noobth.pulsebackend.dto.CreateApiRequestDto;
import dev.noobth.pulsebackend.dto.CreateApiResponseDto;
import dev.noobth.pulsebackend.dto.Method;
import dev.noobth.pulsebackend.repository.ApiRepository;
import dev.noobth.pulsebackend.repository.CheckResultRepository;
import dev.noobth.pulsebackend.scheduler.MonitoringScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiServiceTest {

    @Mock
    private ApiRepository apiRepository;

    @Mock
    private CheckResultRepository checkResultRepository;

    @Mock
    private MonitoringScheduler monitoringScheduler;

    @InjectMocks
    private ApiService apiService;

    private CreateApiRequestDto createRequest() {
        return new CreateApiRequestDto("https://example.com", Method.GET, 180, 5, 0, 3, 3600);
    }

    private CheckResult createCheckResult(String apiId, String checkedAt, boolean success) {
        CheckResult r = new CheckResult();
        r.setApiId(apiId);
        r.setCheckedAt(checkedAt);
        r.setSuccess(success);
        return r;
    }

    private Api createApi(String apiId, boolean enabled) {
        Api api = new Api();
        api.setApiId(apiId);
        api.setUrl("https://example.com");
        api.setMethod("GET");
        api.setEnabled(enabled);
        return api;
    }

    // ---- registerApi ----

    @Test
    void registerApi_savesApiWithCorrectFields() {
        when(apiRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        apiService.registerApi(createRequest());

        ArgumentCaptor<Api> captor = ArgumentCaptor.forClass(Api.class);
        verify(apiRepository).save(captor.capture());
        Api saved = captor.getValue();

        assertThat(saved.getApiId()).isNotBlank();
        assertThat(saved.getUrl()).isEqualTo("https://example.com");
        assertThat(saved.getMethod()).isEqualTo("GET");
        assertThat(saved.getIntervalSeconds()).isEqualTo(180);
        assertThat(saved.getTimeoutSeconds()).isEqualTo(5);
        assertThat(saved.getRetryCount()).isEqualTo(0);
        assertThat(saved.getAlertThreshold()).isEqualTo(3);
        assertThat(saved.getAlertCooldownSeconds()).isEqualTo(3600);
        assertThat(saved.getNextCheckAt()).isNotNull();
        assertThat(saved.isEnabled()).isTrue();
    }

    @Test
    void registerApi_schedulesCheck() {
        when(apiRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        apiService.registerApi(createRequest());

        verify(monitoringScheduler).scheduleNextCheck(any(Api.class));
    }

    @Test
    void registerApi_returnsDto_withApiIdAndMessage() {
        when(apiRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateApiResponseDto response = apiService.registerApi(createRequest());

        assertThat(response.apiId()).isNotBlank();
        assertThat(response.message()).isEqualTo("registered");
    }

    // ---- getAllApis ----

    @Test
    void getAllApis_returnsAllApis() {
        List<Api> apis = List.of(createApi("api1", true), createApi("api2", false));
        when(apiRepository.findAll()).thenReturn(apis);

        List<Api> result = apiService.getAllApis();

        assertThat(result).hasSize(2);
        verify(apiRepository).findAll();
    }

    // ---- getApi ----

    @Test
    void getApi_returnsApi_whenFound() {
        Api api = createApi("api1", true);
        when(apiRepository.findById("api1")).thenReturn(Optional.of(api));

        Api result = apiService.getApi("api1");

        assertThat(result.getApiId()).isEqualTo("api1");
    }

    @Test
    void getApi_throwsException_whenNotFound() {
        when(apiRepository.findById("api1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiService.getApi("api1"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("api1");
    }

    // ---- deleteApi ----

    @Test
    void deleteApi_deletesApi_whenFound() {
        when(apiRepository.findById("api1")).thenReturn(Optional.of(createApi("api1", true)));

        apiService.deleteApi("api1");

        verify(apiRepository).deleteById("api1");
        verify(monitoringScheduler).unschedule("api1");
    }

    @Test
    void deleteApi_throwsException_whenNotFound() {
        when(apiRepository.findById("api1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiService.deleteApi("api1"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("api1");

        verify(apiRepository, never()).deleteById(any());
    }

    // ---- toggleEnabled ----

    @Test
    void toggleEnabled_disablesApi_whenEnabled() {
        Api api = createApi("api1", true);
        when(apiRepository.findById("api1")).thenReturn(Optional.of(api));

        boolean result = apiService.toggleEnabled("api1");

        assertThat(result).isFalse();
        verify(apiRepository).save(api);
        verify(monitoringScheduler).unschedule("api1");
    }

    @Test
    void toggleEnabled_enablesApi_whenDisabled() {
        Api api = createApi("api1", false);
        when(apiRepository.findById("api1")).thenReturn(Optional.of(api));

        boolean result = apiService.toggleEnabled("api1");

        assertThat(result).isTrue();
        verify(apiRepository).save(api);
        verify(monitoringScheduler).scheduleNextCheck(api);
    }

    @Test
    void toggleEnabled_throwsException_whenNotFound() {
        when(apiRepository.findById("api1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiService.toggleEnabled("api1"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("api1");
    }

    // ---- getResults ----

    @Test
    void getResults_returnsSortedDescAndLimited() {
        when(apiRepository.findById("api1")).thenReturn(Optional.of(createApi("api1", true)));
        when(checkResultRepository.findRecentByApiId("api1", 2)).thenReturn(List.of(
                createCheckResult("api1", "2024-01-01T03:00:00Z", false),
                createCheckResult("api1", "2024-01-01T02:00:00Z", true)
        ));

        List<CheckResult> result = apiService.getResults("api1", 2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCheckedAt()).isEqualTo("2024-01-01T03:00:00Z");
        assertThat(result.get(1).getCheckedAt()).isEqualTo("2024-01-01T02:00:00Z");
    }

    @Test
    void getResults_throwsException_whenApiNotFound() {
        when(apiRepository.findById("api1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiService.getResults("api1", 50))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("api1");
    }
}
