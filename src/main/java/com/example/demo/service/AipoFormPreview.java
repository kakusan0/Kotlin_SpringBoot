package com.example.demo.service;

import java.util.List;

public record AipoFormPreview(String category, String note, List<String> routeMembers, String attachedFileName,
                              String submitButtonId, String fileUploadButtonId, boolean fileUploadButtonExists,
                              boolean fileInputExists, boolean ready) {

    public AipoFormPreview withAttachedFileName(String fileName) {
        return new AipoFormPreview(
                category,
                note,
                routeMembers,
                fileName,
                submitButtonId,
                fileUploadButtonId,
                fileUploadButtonExists,
                fileInputExists,
                ready
        );
    }
}
