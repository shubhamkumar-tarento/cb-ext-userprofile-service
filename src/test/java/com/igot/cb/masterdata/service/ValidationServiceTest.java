package com.igot.cb.masterdata.service;

import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ValidationServiceTest {

    @InjectMocks
    private ValidationService validationService;

    @Mock
    private CbServerProperties cbServerProperties;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        when(cbServerProperties.getMasterDataAllowedSortByFields())
                .thenReturn(List.of("name", "description", "id"));
        when(cbServerProperties.getMasterDataSearchStringMinLength()).thenReturn("3");
        when(cbServerProperties.getMasterDataSearchStringMaxLength()).thenReturn("10");
        when(cbServerProperties.getMasterDataSearchStringRegex()).thenReturn("^[a-zA-Z0-9 ]+$");
    }

    // ---------- validateSearchRequest ----------

    @Test
    void validateSearchRequest_emptyBody() {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.validateSearchRequest(api, Map.of());
        assertFalse(result);
        assertEquals(HttpStatus.BAD_REQUEST, api.getResponseCode());
    }

    @Test
    void validateSearchRequest_pageNumberNotNumeric() {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.validateSearchRequest(api,
                Map.of(Constants.REQUEST, Map.of(Constants.PAGE_NUMBER, "abc")));
        assertFalse(result);
        assertEquals("pageNumber must be numeric", api.getParams().getErrMsg());
    }

    @Test
    void validateSearchRequest_pageNumberNegative() {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.validateSearchRequest(api,
                Map.of(Constants.REQUEST, Map.of(Constants.PAGE_NUMBER, -1)));
        assertFalse(result);
    }

    @Test
    void validateSearchRequest_pageSizeInvalid() {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.validateSearchRequest(api,
                Map.of(Constants.REQUEST, Map.of(Constants.PAGE_SIZE, 0)));
        assertFalse(result);
    }

    @Test
    void validateSearchRequest_sortByNotAllowed() {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.validateSearchRequest(api,
                Map.of(Constants.REQUEST, Map.of(Constants.SORT_BY, "hack")));
        assertFalse(result);
        assertEquals("sortBy field is not allowed", api.getParams().getErrMsg());
    }

    // ab -> too short, abcdefghijklmnop -> too long, @@@ -> invalid characters
    @ParameterizedTest
    @ValueSource(strings = {"ab", "abcdefghijklmnop", "@@@"})
    void validateSearchRequest_invalidSearchString(String searchString) {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.validateSearchRequest(api,
                Map.of(Constants.REQUEST, Map.of(Constants.SEARCH_STRING, searchString)));
        assertFalse(result);
    }

    // ---------- upsertDegreeValidation ----------

    @Test
    void upsertDegreeValidation_emptyRequest() {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.upsertDegreeValidation(api, Map.of());
        assertFalse(result);
    }

    @Test
    void upsertDegreeValidation_idNotNumeric() {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.upsertDegreeValidation(api,
                Map.of(Constants.REQUEST, Map.of(Constants.ID, "abc")));
        assertFalse(result);
    }

    @Test
    void upsertDegreeValidation_statusNotNumeric() {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.upsertDegreeValidation(api,
                Map.of(Constants.REQUEST, Map.of(Constants.ID, 1, Constants.STATUS, "bad")));
        assertFalse(result);
    }

    @Test
    void upsertDegreeValidation_statusOutOfRange() {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.upsertDegreeValidation(api,
                Map.of(Constants.REQUEST, Map.of(Constants.ID, 1, Constants.STATUS, 5)));
        assertFalse(result);
    }

    @Test
    void upsertDegreeValidation_emptyName() {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.upsertDegreeValidation(api,
                Map.of(Constants.REQUEST, Map.of(Constants.NAME, "  ")));
        assertFalse(result);
    }

    @Test
    void upsertDegreeValidation_descriptionTooLong() {
        ApiResponse api = new ApiResponse();
        String longDesc = "x".repeat(300);
        boolean result = validationService.upsertDegreeValidation(api,
                Map.of(Constants.REQUEST, Map.of(Constants.NAME, "MBA", Constants.DESCRIPTION, longDesc)));
        assertFalse(result);
    }

    @Test
    void upsertDegreeValidation_invalidNameCharacters() {
        when(cbServerProperties.getDegreeNameRegex()).thenReturn("^[\\p{L}\\p{N}\\s.,'&()/-]+$");
        ApiResponse api = new ApiResponse();
        boolean result = validationService.upsertDegreeValidation(api,
                Map.of(Constants.REQUEST, Map.of(Constants.NAME, "!@#$%^&*()")));
        assertFalse(result);
        assertEquals("Degree name contains invalid characters", api.getParams().getErrMsg());
    }

    @Test
    void upsertDegreeValidation_validNameCharacters() {
        when(cbServerProperties.getDegreeNameRegex()).thenReturn("^[\\p{L}\\p{N}\\s.,'&()/-]+$");
        ApiResponse api = new ApiResponse();
        boolean result = validationService.upsertDegreeValidation(api,
                Map.of(Constants.REQUEST, Map.of(Constants.NAME, "B.Tech")));
        assertTrue(result);
    }

    // ---------- upsertInstituteValidation ----------

    @Test
    void upsertInstituteValidation_statusInvalid() {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.upsertInstituteValidation(api,
                Map.of(Constants.REQUEST, Map.of(Constants.ID, 1, Constants.STATUS, 9)));
        assertFalse(result);
    }

    @Test
    void upsertInstituteValidation_nameMissing() {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.upsertInstituteValidation(api,
                Map.of(Constants.REQUEST, Map.of()));
        assertFalse(result);
    }

    @Test
    void upsertInstituteValidation_invalidNameCharacters() {
        when(cbServerProperties.getInstituteNameRegex()).thenReturn("^[\\p{L}\\p{N}\\s.,'&()/-]+$");
        ApiResponse api = new ApiResponse();
        boolean result = validationService.upsertInstituteValidation(api,
                Map.of(Constants.REQUEST, Map.of(Constants.NAME, "!@#$%^&*()")));
        assertFalse(result);
        assertEquals("Institute name contains invalid characters", api.getParams().getErrMsg());
    }

    @Test
    void upsertInstituteValidation_validNameCharacters() {
        when(cbServerProperties.getInstituteNameRegex()).thenReturn("^[\\p{L}\\p{N}\\s.,'&()/-]+$");
        ApiResponse api = new ApiResponse();
        boolean result = validationService.upsertInstituteValidation(api,
                Map.of(Constants.REQUEST, Map.of(Constants.NAME, "St. Xavier's College")));
        assertTrue(result);
    }

    // ---------- additional validateSearchRequest branch coverage ----------

    @Test
    void validateSearchRequest_nestedRequestMissing_createsEmptyRequestAndPasses() {
        ApiResponse api = new ApiResponse();
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("foo", "bar");
        boolean result = validationService.validateSearchRequest(api, requestBody);
        assertTrue(result);
    }

    @Test
    void validateSearchRequest_validSearchString_returnsTrue() {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.validateSearchRequest(api,
                Map.of(Constants.REQUEST, Map.of(Constants.SEARCH_STRING, "hello")));
        assertTrue(result);
    }

    @Test
    void validateSearchRequest_pageSizeNotNumeric() {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.validateSearchRequest(api,
                Map.of(Constants.REQUEST, Map.of(Constants.PAGE_SIZE, "abc")));
        assertFalse(result);
        assertEquals("pageSize must be numeric", api.getParams().getErrMsg());
    }

    @Test
    void validateSearchRequest_pageSizeValid_returnsTrue() {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.validateSearchRequest(api,
                Map.of(Constants.REQUEST, Map.of(Constants.PAGE_SIZE, 10)));
        assertTrue(result);
    }

    @Test
    void validateSearchRequest_sortByAllowed_returnsTrue() {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.validateSearchRequest(api,
                Map.of(Constants.REQUEST, Map.of(Constants.SORT_BY, "name")));
        assertTrue(result);
    }

    @Test
    void validateSearchRequest_requestFieldNotAMap_triggersCatchBlock() {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.validateSearchRequest(api,
                Map.of(Constants.REQUEST, "not-a-map"));
        assertFalse(result);
        assertEquals("Invalid request parameters", api.getParams().getErrMsg());
    }

    @Test
    void validateSearchString_emptyKeyword_returnsTrue() {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.validateSearchString("", api);
        assertTrue(result);
    }

    // ---------- additional upsertDegreeValidation branch coverage ----------

    @Test
    void upsertDegreeValidation_requestFieldNotAMap_returnsFalse() {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.upsertDegreeValidation(api,
                Map.of(Constants.REQUEST, "not-a-map"));
        assertFalse(result);
    }

    @Test
    void upsertDegreeValidation_idValidNoStatus_returnsTrue() {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.upsertDegreeValidation(api,
                Map.of(Constants.REQUEST, Map.of(Constants.ID, 1)));
        assertTrue(result);
    }

    @Test
    void upsertDegreeValidation_statusValidNumericString_returnsTrue() {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.upsertDegreeValidation(api,
                Map.of(Constants.REQUEST, Map.of(Constants.ID, 1, Constants.STATUS, "1")));
        assertTrue(result);
    }

    @Test
    void upsertDegreeValidation_descriptionWithinLimit_returnsTrue() {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.upsertDegreeValidation(api,
                Map.of(Constants.REQUEST, Map.of(Constants.NAME, "MBA", Constants.DESCRIPTION, "short desc")));
        assertTrue(result);
    }

    // ---------- additional upsertInstituteValidation branch coverage ----------

    @Test
    void upsertInstituteValidation_nameEmpty_whenNameFieldMissing() {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.upsertInstituteValidation(api,
                Map.of(Constants.REQUEST, Map.of("dummy", "x")));
        assertFalse(result);
        assertEquals("Institute name cannot be empty", api.getParams().getErrMsg());
    }

    @Test
    void upsertInstituteValidation_success_whenRegexNotConfigured() {
        ApiResponse api = new ApiResponse();
        boolean result = validationService.upsertInstituteValidation(api,
                Map.of(Constants.REQUEST, Map.of(Constants.NAME, "Valid College")));
        assertTrue(result);
    }
}