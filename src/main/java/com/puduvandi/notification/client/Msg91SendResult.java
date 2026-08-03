package com.puduvandi.notification.client;

public record Msg91SendResult(boolean success, String errorMessage) {

    public static Msg91SendResult ok() {
        return new Msg91SendResult(true, null);
    }

    public static Msg91SendResult failure(String errorMessage) {
        return new Msg91SendResult(false, errorMessage);
    }
}
