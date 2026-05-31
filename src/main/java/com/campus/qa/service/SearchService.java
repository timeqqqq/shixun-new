package com.campus.qa.service;

import com.campus.qa.vo.SearchResultItem;
import java.util.List;

public interface SearchService {
    List<SearchResultItem> search(String keyword, String userIp);
}
