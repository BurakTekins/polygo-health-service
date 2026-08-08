package com.polygo.health.application.service.impl;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationLoopService {

    private final NotificationFlushService flushService;

    private volatile boolean running = true;
    private Thread workerThread;

    @PostConstruct
    public void start() {
        workerThread = new Thread(this::loop, "notification-flush-loop");
        workerThread.setDaemon(true);
        workerThread.start();
        log.info("Notification flush loop started");
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
        log.info("Notification flush loop stopped");
    }

    private void loop() {
        while (running) {
            try {
                flushService.flushPendingNotifications();
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("[ERROR] Notification loop error", e);
            }
        }
    }
}
