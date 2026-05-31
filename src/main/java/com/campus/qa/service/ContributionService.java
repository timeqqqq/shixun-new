package com.campus.qa.service;

import com.campus.qa.dto.ContributionSubmitRequest;
import com.campus.qa.entity.Contribution;
import java.util.List;

public interface ContributionService {
    Contribution submit(ContributionSubmitRequest request, String userIp);

    List<Contribution> mine(String userIp);
}
