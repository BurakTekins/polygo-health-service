package com.polygo.health.interfaces.rest.controller;

import com.polygo.health.application.service.TelegramTestService;
import com.polygo.health.interfaces.rest.request.TelegramTestRequest;
import com.polygo.health.interfaces.rest.response.TelegramTestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${services.endpoints.telegram-base}")
@RequiredArgsConstructor
public class TelegramTestController {

    private final TelegramTestService telegramTestService;

    @PostMapping("${services.endpoints.telegram-test}")
    @Operation(
        summary = "Send a Telegram notification"
    )
    @RequestBody(
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(
                example = "{\"message\": \"🧪 Test mesajı \"}"
            )
        )
    )
    @ApiResponse(
        responseCode = "200",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(
                example = "{\"success\": true, \"message\": \"Telegram mesajı başarıyla gönderildi\", \"sentAt\": \"2024-01-14T10:30:00Z\"}"
            )
        )
    )
    @ApiResponse(
        responseCode = "500",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(
                example = "{\"success\": false, \"message\": \"Telegram mesajı gönderilemedi: Connection timeout\", \"sentAt\": \"2024-01-14T10:30:00Z\"}"
            )
        )
    )
    public TelegramTestResponse testTelegram(@org.springframework.web.bind.annotation.RequestBody(required = false) TelegramTestRequest request) {
        return telegramTestService.sendTestMessage(request);
    }
}
