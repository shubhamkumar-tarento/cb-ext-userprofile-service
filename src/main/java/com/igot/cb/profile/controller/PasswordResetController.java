package com.igot.cb.profile.controller;

import com.igot.cb.profile.service.PasswordResetService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/user/v2/reset/password")
    public ResponseEntity<ApiResponse> resetPassword(@RequestHeader(Constants.X_AUTH_TOKEN) String authToken){
        ApiResponse apiResponse = passwordResetService.resetPassword(authToken);
        return new ResponseEntity<>(apiResponse, apiResponse.getResponseCode());
    }
}
