package com.example.demo.service;

public record AipoLoginResult(boolean success, String message, String sessionId, String workflowUrl,
                              String createRequestUrl, boolean timesheetSelected, boolean fileUploaded,
                              AipoFormPreview formPreview, boolean autoSubmitted) {
}
