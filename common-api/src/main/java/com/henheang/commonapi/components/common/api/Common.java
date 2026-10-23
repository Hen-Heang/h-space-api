package com.henheang.commonapi.components.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Common {
    private String api_id;
    private String request_id;

    @JsonProperty("device_id")
    private String deviceId;

    public Common(Map<String, String> header) {
        this.api_id = header.get("x-api-id");
        this.request_id = header.get("x-request-id");
        this.deviceId = header.get("x-device-id");
    }
}
