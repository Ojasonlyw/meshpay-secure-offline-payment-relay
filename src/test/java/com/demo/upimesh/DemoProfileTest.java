package com.demo.upimesh;

import com.demo.upimesh.controller.DemoController;
import com.demo.upimesh.exception.InvalidSignatureException;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.repository.DeviceKeyRepository;
import com.demo.upimesh.repository.DeviceRepository;
import com.demo.upimesh.service.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class DemoProfileTest {
    @ParameterizedTest
    @ValueSource(strings = {"dev", "demo", "test", "postgres", "dev,postgres", "test,postgres"})
    void helpersAreRestrictedEvenWithMixedProfiles(String profiles) {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles.split(",")))
                .withBean(PaymentService.class, () -> mock(PaymentService.class))
                .withBean(MeshService.class, () -> mock(MeshService.class))
                .withUserConfiguration(DemoController.class)
                .run(context -> {
                    if (profiles.contains("postgres")) assertThat(context).doesNotHaveBean(DemoController.class);
                    else assertThat(context).hasSingleBean(DemoController.class);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"postgres", "dev,postgres", "test,postgres"})
    void postgresCannotUseSigningKeys(String profiles) {
        var environment = new StandardEnvironment();
        environment.setActiveProfiles(profiles.split(","));
        var devices = mock(DeviceRepository.class);
        var keys = mock(DeviceKeyRepository.class);
        var signatures = mock(DeviceSignatureService.class);
        var service = new DemoDeviceKeyService(devices, keys, signatures, environment, "target/disabled-demo-keys");
        var instruction = new PaymentInstruction();
        instruction.setDeviceId("phone-alice");
        assertThrows(InvalidSignatureException.class, () -> service.sign(instruction));
        verifyNoInteractions(devices, keys, signatures);
    }
}
