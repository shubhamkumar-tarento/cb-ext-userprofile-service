package com.igot.cb.exceptions;

import com.igot.cb.util.Constants;
import org.apache.commons.lang3.StringUtils;

import java.text.MessageFormat;

public class ProjectCommonException extends RuntimeException {

    /** serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** code String code ResponseCode. */
    private final String errorCode;
    /** message String ResponseCode. */
    private final String errorMessage;
    /** responseCode int ResponseCode. */
    private final int errorResponseCode;

    private final ResponseCode responseCode;

    /**
     * This code is for client to identify the error and based on that do the message localization.
     *
     * @return String
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * message for client in english.
     *
     * @return String
     */
    @Override
    public String getMessage() {
        return errorMessage;
    }

    /**
     * This method will provide response code, this code will be used in response header.
     *
     * @return int
     */
    public int getErrorResponseCode() {
        return errorResponseCode;
    }

    public ResponseCode getResponseCode() {
        return responseCode;
    }

    public String getErrorMessage() {
        return getMessage();
    }

    /**
     * three argument constructor.
     *
     * @param code String
     * @param message String
     * @param responseCode int
     */
    public ProjectCommonException(ResponseCode code, String message, int responseCode) {
        super();
        this.responseCode = code;
        this.errorCode = code.getErrorCode();
        this.errorMessage = message;
        this.errorResponseCode = responseCode;
    }

    public ProjectCommonException(ProjectCommonException pce, String actorOperation) {
        super();
        super.setStackTrace(pce.getStackTrace());
        this.errorCode =
                new StringBuilder(Constants.USER_ORG_SERVICE_PREFIX)
                        .append(actorOperation)
                        .append(pce.getErrorCode())
                        .toString();
        this.errorResponseCode = pce.getErrorResponseCode();
        this.errorMessage = pce.getMessage();
        this.responseCode = pce.getResponseCode();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(errorCode).append(": ");
        builder.append(errorMessage);
        return builder.toString();
    }

    public ProjectCommonException(
            ResponseCode code,
            String messageWithPlaceholder,
            int responseCode,
            String... placeholderValue) {
        super();
        this.errorCode = code.getErrorCode();
        this.errorMessage = MessageFormat.format(messageWithPlaceholder, placeholderValue);
        this.errorResponseCode = responseCode;
        this.responseCode = code;
    }

    public static void throwServerErrorException(ResponseCode responseCode, String exceptionMessage) {
        throw new ProjectCommonException(
                responseCode,
                StringUtils.isBlank(exceptionMessage) ? responseCode.getErrorMessage() : exceptionMessage,
                ResponseCode.SERVER_ERROR.getStatusCode());
    }

    public static void throwServerErrorException(ResponseCode responseCode) {
        throwServerErrorException(responseCode, responseCode.getErrorMessage());
    }

}
