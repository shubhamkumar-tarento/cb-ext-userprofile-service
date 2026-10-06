package com.igot.cb.exceptions;

import com.igot.cb.util.Constants;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * @author Manzarul
 */
@Getter
public enum ResponseCode {
    UNAUTHORIZED(ResponseMessage.Key.UNAUTHORIZED_USER, ResponseMessage.Message.UNAUTHORIZED_USER),
    INTERNAL_ERROR(ResponseMessage.Key.INTERNAL_ERROR, ResponseMessage.Message.INTERNAL_ERROR),
    RESOURCE_NOT_FOUND(
            ResponseMessage.Key.RESOURCE_NOT_FOUND, ResponseMessage.Message.RESOURCE_NOT_FOUND),
    INVALID_PARAMETER_VALUE(
            ResponseMessage.Key.INVALID_PARAMETER_VALUE, ResponseMessage.Message.INVALID_PARAMETER_VALUE),

    OK(200),
    CLIENT_ERROR(400),
    SERVER_ERROR(500);

    /**
     * Empty message placeholder returned for response codes constructed without a textual message.
     */
    public static final String EMPTY_MESSAGE = "";

    @Setter
    private int statusCode;
    /**
     * error code contains String value
     */
    private String errorCode;
    /**
     * errorMessage contains proper error message.
     */
    private String errorMessage;

    /**
     * @param errorCode    String
     * @param errorMessage String
     */
    ResponseCode(String errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    ResponseCode(int statusCode) {
        this.statusCode = statusCode;
    }

    /**
     * This method will provide ResponseCode enum based on error code
     */
    public static ResponseCode getResponse(String errorCode) {
        if (StringUtils.isBlank(errorCode)) {
            return null;
        } else if (Constants.UNAUTHORIZED.equals(errorCode)) {
            return ResponseCode.UNAUTHORIZED;
        } else {
            ResponseCode value = null;
            ResponseCode[] responseCodes = ResponseCode.values();
            for (ResponseCode response : responseCodes) {
                if (response.getErrorCode() != null && response.getErrorCode().equals(errorCode)) {
                    return response;
                }
            }
            return value;
        }
    }

}