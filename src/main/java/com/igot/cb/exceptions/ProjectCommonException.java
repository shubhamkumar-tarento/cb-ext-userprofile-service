package com.igot.cb.exceptions;

import com.igot.cb.util.Constants;
import org.apache.commons.lang3.StringUtils;

import java.text.MessageFormat;

public class ProjectCommonException extends RuntimeException {

    /** serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** code String code ResponseCode. */
    private String errorCode;
    /** message String ResponseCode. */
    private String errorMessage;
    /** responseCode int ResponseCode. */
    private int errorResponseCode;

    private ResponseCode responseCode;

    /**
     * This code is for client to identify the error and based on that do the message localization.
     *
     * @return String
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * To set the client code.
     *
     * @param code String
     */
    public void setErrorCode(String code) {
        this.errorCode = code;
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

    /** @param message String */
    public void setMessage(String message) {
        this.errorMessage = message;
    }

    /**
     * This method will provide response code, this code will be used in response header.
     *
     * @return int
     */
    public int getErrorResponseCode() {
        return errorResponseCode;
    }

    /** @param responseCode int */
    public void setErrorResponseCode(int responseCode) {
        this.errorResponseCode = responseCode;
    }

    public ResponseCode getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(ResponseCode responseCode) {
        this.responseCode = responseCode;
    }

    public String getErrorMessage() {
        return getMessage();
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
