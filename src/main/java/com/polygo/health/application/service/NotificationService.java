package com.polygo.health.application.service;

public interface NotificationService {
    void sendNotification(String message);
    void sendDailySummary(String message);
}
