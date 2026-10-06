package com.igot.cb.extendedprofile.controller;

import com.igot.cb.extendedprofile.service.ExtendedProfileService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

/**
 * @author mahesh.vakkund
 */
@RestController
@RequestMapping("/v1/extendedprofile")
@RequiredArgsConstructor
public class ExtendedProfileController {

    private final ExtendedProfileService extendedProfileService;

    @GetMapping(value = "/list/states")
    public ResponseEntity<ApiResponse> getStatesList(@RequestHeader(Constants.X_AUTH_TOKEN) String authToken) {
        return new ResponseEntity<>(extendedProfileService.getStatesList(authToken), HttpStatus.OK);
    }

    @PostMapping(value = "/list/districts")
    public ResponseEntity<ApiResponse> getDistrictsList(@RequestHeader(Constants.X_AUTH_TOKEN) String authToken,
                                                        @Valid @RequestBody Map<String, Object> requestBody) {
        return new ResponseEntity<>(extendedProfileService.getDistrictsList(authToken,requestBody), HttpStatus.OK);
    }
}
