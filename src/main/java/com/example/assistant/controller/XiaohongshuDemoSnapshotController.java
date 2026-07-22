package com.example.assistant.controller;

import com.example.assistant.dto.XiaohongshuDemoSnapshotResponse;
import com.example.assistant.service.XiaohongshuDemoSnapshotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo-snapshots")
public class XiaohongshuDemoSnapshotController {

    private final XiaohongshuDemoSnapshotService demoSnapshotService;

    public XiaohongshuDemoSnapshotController(XiaohongshuDemoSnapshotService demoSnapshotService) {
        this.demoSnapshotService = demoSnapshotService;
    }

    @GetMapping("/xiaohongshu")
    public XiaohongshuDemoSnapshotResponse xiaohongshu(@RequestParam(required = false) String userId) {
        return demoSnapshotService.snapshot(userId);
    }
}
