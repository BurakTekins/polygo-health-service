package com.polygo.health.application.service;

import com.polygo.health.interfaces.rest.request.TelegramTestRequest;
import com.polygo.health.interfaces.rest.response.TelegramTestResponse;

public interface TelegramTestService {
    TelegramTestResponse sendTestMessage(TelegramTestRequest request);
}
