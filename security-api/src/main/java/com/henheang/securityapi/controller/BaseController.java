package com.henheang.securityapi.controller;

import com.henheang.commonapi.components.common.api.ApiResponse;
import com.henheang.commonapi.components.common.api.Common;
import com.henheang.commonapi.components.common.api.EmptyJsonResponse;
import com.henheang.commonapi.components.common.api.StatusCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public abstract class BaseController {
    public <T> ResponseEntity<ApiResponse<?>> buildResponse(
            HttpStatus status, T data, HttpHeaders headers) {

        ApiResponse<?> apiResponse =
                ApiResponse.builder().status(StatusCode.SUCCESS).data(data).build();

        return ResponseEntity.ok().headers(headers).body(apiResponse);
    }

    public <T> ResponseEntity<ApiResponse<?>> buildResponse(
            HttpStatus status, T data, HttpHeaders headers, Common common) {

        ApiResponse<?> apiResponse =
                ApiResponse.builder().status(StatusCode.SUCCESS).data(data).common(common).build();

        return ResponseEntity.ok().headers(headers).body(apiResponse);
    }

    public <T> ResponseEntity<ApiResponse<?>> ok(T data, HttpHeaders headers) {

        return buildResponse(HttpStatus.OK, data, headers);
    }

    public <T> ResponseEntity<ApiResponse<?>> buildResponse(HttpStatus status, T data) {

        return buildResponse(status, data, new HttpHeaders());
    }

    public <T> ResponseEntity<ApiResponse<?>> ok(T data) {

        return buildResponse(HttpStatus.OK, data);
    }

    public <T> ResponseEntity<ApiResponse<?>> ok(T data, Common common) {

        return buildResponse(HttpStatus.OK, data, new HttpHeaders(), common);
    }

    public <T> ResponseEntity<ApiResponse<?>> buildResponse(HttpStatus status) {

        return buildResponse(status, new EmptyJsonResponse());
    }

    public <T> ResponseEntity<ApiResponse<?>> ok() {

        return buildResponse(HttpStatus.OK);
    }

    public <T> ResponseEntity<ApiResponse<?>> ok(Common common) {

        return buildResponse(HttpStatus.OK, new EmptyJsonResponse(), new HttpHeaders(), common);
    }
}
