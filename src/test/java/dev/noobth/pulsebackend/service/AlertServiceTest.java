package dev.noobth.pulsebackend.service;

import dev.noobth.pulsebackend.domain.Api;
import dev.noobth.pulsebackend.repository.ApiRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private SnsClient snsClient;

    @Mock
    private ApiRepository apiRepository;

    @InjectMocks
    private AlertService alertService;

    private Api createApi(int failures, int threshold) {
        Api api = new Api();
        api.setApiId("api1");
        api.setUrl("http://test.com");
        api.setConsecutiveFailures(failures);
        api.setAlertThreshold(threshold);
        api.setAlertSentAt(null);
        api.setAlertCooldownSeconds(60);
        return api;
    }

    @Test
    void shouldSendAlert_whenThresholdExceeded() {
        // given
        Api api = createApi(5, 3);
        when(apiRepository.findById("api1")).thenReturn(Optional.of(api));

        // when
        alertService.alertIfNeeded("api1");

        // then
        ArgumentCaptor<PublishRequest> captor =
                ArgumentCaptor.forClass(PublishRequest.class);

        verify(snsClient).publish(captor.capture());
        verify(apiRepository).updateAlertSentAt(eq("api1"), anyString());

        PublishRequest request = captor.getValue();
        assertThat(request.message()).contains("api1");
        assertThat(request.message()).contains("http://test.com");
    }

    @Test
    void shouldNotSendAlert_whenBelowThreshold() {
        // given
        Api api = createApi(1, 3);
        when(apiRepository.findById("api1")).thenReturn(Optional.of(api));

        // when
        alertService.alertIfNeeded("api1");

        // then
        verify(snsClient, never()).publish(any(PublishRequest.class));
        verify(apiRepository, never()).updateAlertSentAt(anyString(), anyString());
    }
}