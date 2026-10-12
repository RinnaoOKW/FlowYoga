package com.flowyoga.course.client;

public record MemberCardProbeResponse(
        String service,
        boolean probe,
        String cardType,
        int remainingSessions,
        String requestId,
        String message) {
}
