package com.example.assistant.controller;

import com.example.assistant.dto.ImportResultResponse;
import com.example.assistant.service.HistoryImportJob;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/history-import")
public class HistoryImportController {

    private final HistoryImportJob historyImportJob;

    public HistoryImportController(HistoryImportJob historyImportJob) {
        this.historyImportJob = historyImportJob;
    }

    @PostMapping("/run")
    public List<ImportResultResponse> runOnce() {
        return historyImportJob.importOnce();
    }
}
