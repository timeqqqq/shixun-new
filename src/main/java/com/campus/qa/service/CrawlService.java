package com.campus.qa.service;

import com.campus.qa.dto.CrawlStartRequest;
import com.campus.qa.dto.CrawlStartResponse;
import com.campus.qa.entity.CrawlTask;
import java.util.List;

public interface CrawlService {
    CrawlStartResponse startCrawl(CrawlStartRequest request);

    List<CrawlTask> listTasks();
}
