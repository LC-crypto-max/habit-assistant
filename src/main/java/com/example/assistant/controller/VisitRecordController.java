package com.example.assistant.controller;

import com.example.assistant.dto.ActivityResponse;
import com.example.assistant.dto.ImportResultResponse;
import com.example.assistant.dto.VisitRecordRequest;
import com.example.assistant.service.VisitRecordService;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/visits")
public class VisitRecordController {

    private final VisitRecordService visitRecordService;

    public VisitRecordController(VisitRecordService visitRecordService) {
        this.visitRecordService = visitRecordService;
    }

    @PostMapping
    public ActivityResponse recordVisit(@Valid @RequestBody VisitRecordRequest request) {
        return visitRecordService.recordVisit(request);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResultResponse importVisits(@RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String userId) throws IOException {
        return visitRecordService.importCsv(file, userId);
    }
}
