package com.campus.qa.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.qa.dto.admin.BatchRejectRequest;
import com.campus.qa.dto.admin.EditApproveRequest;
import com.campus.qa.dto.admin.RejectRequest;
import com.campus.qa.service.AdminContributionAuditService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/contributions")
public class AdminContributionController {

    private final AdminContributionAuditService auditService;

    public AdminContributionController(AdminContributionAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public IPage<Map<String, Object>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "status", required = false) String status) {
        return auditService.list(page, size, status);
    }

    @PutMapping("/{id}/approve")
    public Map<String, String> approve(@PathVariable Long id) {
        auditService.approve(id);
        return Map.of("message", "approved");
    }

    @PutMapping("/{id}/reject")
    public Map<String, String> reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        auditService.reject(id, request.getReason());
        return Map.of("message", "rejected");
    }

    @PutMapping("/{id}/edit-and-approve")
    public Map<String, String> editAndApprove(@PathVariable Long id, @Valid @RequestBody EditApproveRequest request) {
        auditService.editAndApprove(id, request);
        return Map.of("message", "edited and approved");
    }

    @DeleteMapping
    public Map<String, String> batchReject(@Valid @RequestBody BatchRejectRequest request) {
        auditService.batchReject(request.getIds(), request.getReason());
        return Map.of("message", "batch rejected");
    }
}
