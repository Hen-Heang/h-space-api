package com.henheang.commonapi.components.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude
public class ApiResponse<T> {

    @JsonProperty("status")
    private ApiStatus statusCode;

    private T data;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Common common;

    public ApiResponse(T data) {
        this.data = data;
    }

    public ApiResponse(ApiStatus statusCode, T data) {
        this.statusCode = statusCode;
        this.data = data;
    }

    public ApiResponse(ApiStatus statusCode, T data, Common common) {
        this.statusCode = statusCode;
        this.data = data;
        this.common = common;
    }

    @Builder
    public ApiResponse(StatusCode status, T data, Common common) {
        this.statusCode = new ApiStatus(status);
        this.data = data;
        this.common = common;
    }
}
