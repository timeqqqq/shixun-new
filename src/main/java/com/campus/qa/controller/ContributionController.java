package com.campus.qa.controller;

import com.campus.qa.dto.ContributionSubmitRequest;
import com.campus.qa.entity.Contribution;
import com.campus.qa.service.ContributionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contributions")
public class ContributionController {

    private final ContributionService contributionService;

    public ContributionController(ContributionService contributionService) {
        this.contributionService = contributionService;
    }

    @PostMapping
    public Contribution submit(@Valid @RequestBody ContributionSubmitRequest request, HttpServletRequest httpRequest) {
        return contributionService.submit(request, httpRequest.getRemoteAddr());
    }

    @GetMapping("/mine")
    public List<Contribution> mine(HttpServletRequest httpRequest) {
        return contributionService.mine(httpRequest.getRemoteAddr());
    }
}
