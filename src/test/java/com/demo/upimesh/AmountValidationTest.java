package com.demo.upimesh;

import com.demo.upimesh.dto.request.DemoSendRequest;
import com.demo.upimesh.dto.request.PaymentRequest;
import com.demo.upimesh.model.PaymentInstruction;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class AmountValidationTest {
    private final ObjectMapper json = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "0.10", "12.34", "1000000", "1000000.00"})
    void exactBoundaryAmounts(String value) throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            for (Class<?> type : new Class<?>[]{DemoSendRequest.class, PaymentRequest.class, PaymentInstruction.class}) {
                Object input = json.readValue("{\"senderVpa\":\"a\",\"receiverVpa\":\"b\",\"amount\":" + value + "}", type);
                assertTrue(factory.getValidator().validateProperty(input, "amount").isEmpty());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "1.000", "1e2", "1E-2", "1000000.01", "100000000000000.01", "\"bad\"", "\"\"", "null"})
    void invalidAmountsNeverRound(String value) throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            for (Class<?> type : new Class<?>[]{DemoSendRequest.class, PaymentRequest.class, PaymentInstruction.class}) {
                try {
                    Object input = json.readValue("{\"amount\":" + value + "}", type);
                    assertFalse(factory.getValidator().validateProperty(input, "amount").isEmpty(), type + ": " + value);
                } catch (com.fasterxml.jackson.core.JsonProcessingException expected) {
                    // Invalid decimal syntax is rejected before bean validation.
                }
            }
        }
    }
}
