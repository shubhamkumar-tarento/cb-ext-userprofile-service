package com.igot.cb.profile.controller;

import com.igot.cb.profile.service.ProfileService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/user/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @PostMapping("/extended")
    public ResponseEntity<ApiResponse> saveExtendedProfile(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken,
            @RequestBody Map<String, Object> request) throws Exception {
        ApiResponse response = profileService.saveExtendedProfile(request, authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/extended/all/{userId}")
    public ResponseEntity<Object> getExtendedProfileSummary(@PathVariable(Constants.USER_ID_RQST) String userId,
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        ApiResponse response = profileService.getExtendedProfileSummary(userId, authToken);
        return new ResponseEntity<>(response, HttpStatus.valueOf(response.getResponseCode().value()));
    }

    @GetMapping("/extended/serviceHistory/{userId}")
    public ResponseEntity<Object> getServiceHistory(@PathVariable(Constants.USER_ID_RQST) String userId,
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        ApiResponse response = profileService.readFullExtendedProfile(userId, Constants.SERVICE_HISTORY, authToken);
        return new ResponseEntity<>(response, HttpStatus.valueOf(response.getResponseCode().value()));
    }

    @GetMapping("/extended/education/{userId}")
    public ResponseEntity<Object> getEducationalQualifications(@PathVariable(Constants.USER_ID_RQST) String userId,
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        ApiResponse response = profileService.readFullExtendedProfile(userId, Constants.EDUCATION_QUALIFICATION,
                authToken);
        return new ResponseEntity<>(response, HttpStatus.valueOf(response.getResponseCode().value()));
    }

    @GetMapping("/extended/locationDetails/{userId}")
    public ResponseEntity<Object> getLocationDetails(@PathVariable(Constants.USER_ID_RQST) String userId,
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        ApiResponse response = profileService.readFullExtendedProfile(userId, Constants.LOCATION_DETAILS, authToken);
        return new ResponseEntity<>(response, HttpStatus.valueOf(response.getResponseCode().value()));
    }

    @GetMapping("/extended/achievements/{userId}")
    public ResponseEntity<Object> getAchievements(@PathVariable(Constants.USER_ID_RQST) String userId,
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        ApiResponse response = profileService.readFullExtendedProfile(userId, Constants.ACHIEVEMENTS, authToken);
        return new ResponseEntity<>(response, HttpStatus.valueOf(response.getResponseCode().value()));
    }

    @PutMapping("/extended")
    public ResponseEntity<ApiResponse> updateExtendedProfile(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken,
            @RequestBody Map<String, Object> request) throws Exception {
        ApiResponse response = profileService.updateExtendedProfile(request, authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @DeleteMapping("/extended")
    public ResponseEntity<ApiResponse> deleteExtendedProfile(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken,
            @RequestBody Map<String, Object> request) {

        ApiResponse response = profileService.deleteExtendedProfile(request, authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/basic/{userId}")
    public ResponseEntity<Object> getBasicProfileForAdmin(@PathVariable(Constants.USER_ID_RQST) String userId,
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        ApiResponse response = profileService.getBasicProfile(userId, authToken,false);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/extended/competencies/{userId}")
    public ResponseEntity<Object> getCompetencies(@PathVariable(Constants.USER_ID_RQST) String userId,
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        ApiResponse response = profileService.listCompetencies(userId, authToken);
        return new ResponseEntity<>(response, HttpStatus.valueOf(response.getResponseCode().value()));
    }

    @PostMapping(value = "/update/additionalFields")
    public ResponseEntity<ApiResponse> updateAdditionalFields(
            @RequestHeader(Constants.X_AUTH_TOKEN) String authToken,
            @RequestHeader(Constants.X_AUTH_USER_ORG_ID) String orgId,
            @RequestBody Map<String, Object> requestBody) {
        ApiResponse response = profileService.updateAdditionalFields(requestBody,orgId, authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/getAdditionalFields/{userId}/{orgId}")
    public ResponseEntity<Object> getAdditionalFieldsByOrg(
            @PathVariable String userId,
            @PathVariable String orgId,
            @RequestHeader(value = Constants.X_AUTH_TOKEN) String authToken) {
        ApiResponse response = profileService.getAdditionalFieldsByOrg(userId, orgId, authToken,false);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/v1/basic")
    public ResponseEntity<Object> getBasicProfileForVolunteer(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        ApiResponse response = profileService.getBasicProfile("", authToken, true);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/v1/extended/all")
    public ResponseEntity<Object> getExtendedProfileSummaryForNgo(@RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        ApiResponse response = profileService.getExtendedProfileSummary("", authToken);
        return new ResponseEntity<>(response, HttpStatus.valueOf(response.getResponseCode().value()));
    }

    @GetMapping("/v1/getAdditionalFields")
    public ResponseEntity<Object> getAdditionalFieldsByOrgForNgo(
            @RequestHeader(Constants.X_AUTH_USER_ORG_ID)String userOrgId,
            @RequestHeader(value = Constants.X_AUTH_TOKEN) String authToken) {
        ApiResponse response = profileService.getAdditionalFieldsByOrg("", userOrgId, authToken,true);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/v2/basic")
    public ResponseEntity<Object> getBasicProfileForPublic(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        return getBasicProfileForVolunteer(authToken);
    }

    @GetMapping("/v2/extended/all")
    public ResponseEntity<Object> getExtendedProfileSummaryForUser(@RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        return getExtendedProfileSummaryForNgo(authToken);
    }

    @GetMapping("/v2/getAdditionalFields")
    public ResponseEntity<Object> getAdditionalFieldsByOrgForUser(
            @RequestHeader(Constants.X_AUTH_USER_ORG_ID)String userOrgId,
            @RequestHeader(value = Constants.X_AUTH_TOKEN) String authToken) {
        return getAdditionalFieldsByOrgForNgo(userOrgId, authToken);
    }
}

