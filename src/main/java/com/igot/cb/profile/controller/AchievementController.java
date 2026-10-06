package com.igot.cb.profile.controller;

import com.igot.cb.profile.service.AchievementService;
import com.igot.cb.transactional.elasticsearch.dto.SearchCriteria;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/learner/achievement")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse> createLearnerAchievement(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken,
            @RequestHeader(value = Constants.X_AUTH_USER_ORG_ID, required = true) String rootOrgId,
            @RequestBody Map<String, Object> request){
        ApiResponse response = achievementService.createLearnerAchievement(request, authToken, rootOrgId);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PutMapping("/update")
    public ResponseEntity<ApiResponse> updateLearnerAchievement(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken,
            @RequestHeader(value = Constants.X_AUTH_USER_ORG_ID, required = true) String rootOrgId,
            @RequestBody Map<String, Object> request) {
        ApiResponse response = achievementService.updateLearnerAchievement(request, authToken, rootOrgId);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/read/{achievementId}")
    public ResponseEntity<Object> readLearnerAchievement(@PathVariable(Constants.ACHIEVEMENT_ID) String achievementId,
                                                         @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken,
                                                         @RequestHeader(value = Constants.CONTEXT_TYPE, required = true) String contextType) {
        ApiResponse response = achievementService.readLearnerAchievement(achievementId, authToken, contextType);
        return new ResponseEntity<>(response, HttpStatus.valueOf(response.getResponseCode().value()));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<ApiResponse> deleteLearnerAchievement(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken,
            @RequestBody Map<String, Object> request) throws Exception {
        ApiResponse response = achievementService.deleteLearnerAchievement(request, authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PutMapping("/status/update")
    public ResponseEntity<ApiResponse> statusUpdateLearnerAchievement(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = false) String authToken,
            @RequestBody Map<String, Object> request) {
        ApiResponse response = achievementService.statusUpdateLearnerAchievement(request, authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse> searchLearnerAchievements(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken,
            @RequestBody SearchCriteria searchCriteria) {
        ApiResponse response = achievementService.searchLearnerAchievements(searchCriteria, authToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse> listLearnerAchievementsForAdmin(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken,
            @RequestParam(value = Constants.ID, required = false) String id) {
        ApiResponse response = achievementService.getUserAchievements(authToken, id);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping("/v2/list")
    public ResponseEntity<ApiResponse> listLearnerAchievementsByUserIds(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken,
            @RequestBody Map<String, Object> request) {
        ApiResponse response = achievementService.getUserAchievementsByUserIds(authToken, request);
        return ResponseEntity.status(response.getResponseCode()).body(response);
    }


    @GetMapping("/ngo/list")
    public ResponseEntity<ApiResponse> listLearnerAchievementsForNgo(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        ApiResponse response = achievementService.getUserAchievements(authToken, "");
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("v1/list")
    public ResponseEntity<ApiResponse> listLearnerAchievementsForUser(
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = true) String authToken) {
        return listLearnerAchievementsForNgo(authToken);
    }

}
