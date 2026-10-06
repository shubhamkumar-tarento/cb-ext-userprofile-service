package com.igot.cb.exceptions;

/**
 * This class will hold all the response key and message
 *
 * @author Mahesh
 */
public final class ResponseMessage {

    private ResponseMessage() {
    }

    public static final class Message {

        private Message() {
        }

        public static final String UNAUTHORIZED_USER = "You are not authorized.";
        public static final String INTERNAL_ERROR = "Process failed,please try again later.";
        public static final String RESOURCE_NOT_FOUND = "Requested {0} resource not found";
        public static final String INVALID_PARAMETER_VALUE =
                "Invalid value {0} for parameter {1}. Please provide a valid value.";
    }

    public static final class Key {

        private Key() {
        }

        public static final String UNAUTHORIZED_USER = "UNAUTHORIZED_USER";
        public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
        public static final String RESOURCE_NOT_FOUND = "0013";
        public static final String INVALID_PARAMETER_VALUE = "0017";
    }
}