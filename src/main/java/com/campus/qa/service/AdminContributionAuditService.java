package com.campus.qa.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.qa.dto.admin.EditApproveRequest;
import java.util.List;
import java.util.Map;

public interface AdminContributionAuditService {
    IPage<Map<String, Object>> list(int page, int size, String status);

    void approve(Long id);

    void reject(Long id, String reason);

    void editAndApprove(Long id, EditApproveRequest request);

    void batchReject(List<Long> ids, String reason);
}
