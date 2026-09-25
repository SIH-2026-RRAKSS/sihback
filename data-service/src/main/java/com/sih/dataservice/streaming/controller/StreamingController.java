package com.sih.dataservice.streaming.controller;

import com.sih.dataservice.common.dto.ApiResponse;
import com.sih.dataservice.complaints.entity.EntityType;
import com.sih.dataservice.complaints.entity.FinancialEntity;
import com.sih.dataservice.graph.entity.Transaction;
import com.sih.dataservice.graph.entity.TransactionSource;
import com.sih.dataservice.streaming.dto.StartStreamingRequest;
import com.sih.dataservice.streaming.dto.StreamAlertDto;
import com.sih.dataservice.streaming.dto.StreamingStatusDto;
import com.sih.dataservice.streaming.service.StreamingReplayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Tag(name = "Streaming", description = "Real-time live transaction replay and Server-Sent Events (SSE)")
@RestController
@RequestMapping("/streaming")
public class StreamingController {

    private final StreamingReplayService streamingReplayService;

    public StreamingController(StreamingReplayService streamingReplayService) {
        this.streamingReplayService = streamingReplayService;
    }

    @Operation(summary = "Subscribe to live Server-Sent Events (SSE) transaction and alert stream (FR-STR-1)")
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('CYBER_OFFICER', 'POLICE', 'ADMIN')")
    public SseEmitter subscribeEvents() {
        return streamingReplayService.registerEmitter();
    }

    @Operation(summary = "Start live transaction replay from dataset at adjustable speed (FR-STR-1)")
    @PostMapping("/legacy/start")
    @PreAuthorize("hasAnyRole('CYBER_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<StreamingStatusDto>> startReplay(
            @RequestBody(required = false) StartStreamingRequest request) {
        if (request == null) {
            request = new StartStreamingRequest();
        }
        StreamingStatusDto status = streamingReplayService.startReplay(request);
        return ResponseEntity.ok(ApiResponse.ok(status, "Streaming replay started successfully"));
    }

    @Operation(summary = "Stop ongoing live transaction stream replay")
    @PostMapping("/stop")
    @PreAuthorize("hasAnyRole('CYBER_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<StreamingStatusDto>> stopReplay() {
        StreamingStatusDto status = streamingReplayService.stopReplay();
        return ResponseEntity.ok(ApiResponse.ok(status, "Streaming replay stopped successfully"));
    }

    @Operation(summary = "Step a single transaction through the two-stage gate pipeline (FR-GATE-1, FR-GATE-2)")
    @PostMapping("/step")
    @PreAuthorize("hasAnyRole('CYBER_OFFICER', 'ADMIN', 'POLICE')")
    public ResponseEntity<ApiResponse<StreamAlertDto>> stepTransaction(
            @RequestParam(name = "amount", defaultValue = "100000.0") double amount,
            @RequestParam(name = "utr", required = false) String utr) {

        FinancialEntity sender = new FinancialEntity("step_sender_hash", "enc_sender", null, EntityType.ACCOUNT);
        sender.setId(UUID.randomUUID());
        FinancialEntity receiver = new FinancialEntity("step_receiver_hash", "enc_receiver", null, EntityType.ACCOUNT);
        receiver.setId(UUID.randomUUID());

        String resolvedUtr = utr != null ? utr : "UTR-STEP-" + UUID.randomUUID().toString().substring(0, 8);
        Transaction tx = new Transaction(
                resolvedUtr, sender, receiver,
                BigDecimal.valueOf(amount), Instant.now(), TransactionSource.STREAM
        );
        tx.setId(UUID.randomUUID());

        StreamAlertDto alert = streamingReplayService.step(tx);
        return ResponseEntity.ok(ApiResponse.ok(alert, alert != null ? "Anomaly alert triggered" : "Normal transaction processed"));
    }

    @Operation(summary = "Retrieve current status and telemetry of the streaming engine")
    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('CYBER_OFFICER', 'POLICE', 'ADMIN')")
    public ResponseEntity<ApiResponse<StreamingStatusDto>> getStatus() {
        StreamingStatusDto status = streamingReplayService.getStatus();
        return ResponseEntity.ok(ApiResponse.ok(status));
    }
}

