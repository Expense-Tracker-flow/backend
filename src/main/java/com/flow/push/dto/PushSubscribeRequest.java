package com.flow.push.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Matches the object the browser returns from PushSubscription.toJSON()
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PushSubscribeRequest {

    @NotBlank(message = "Endpoint is required")
    @Size(max = 2048)
    private String endpoint;

    @NotNull(message = "Subscription keys are required")
    @Valid
    private Keys keys;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Keys {
        @NotBlank
        @Size(max = 255)
        private String p256dh;

        @NotBlank
        @Size(max = 255)
        private String auth;
    }
}
