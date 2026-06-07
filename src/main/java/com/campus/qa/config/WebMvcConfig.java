package com.campus.qa.config;

import com.campus.qa.interceptor.AdminAuthInterceptor;
import com.campus.qa.interceptor.IpRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;
    private final IpRateLimitInterceptor ipRateLimitInterceptor;

    public WebMvcConfig(AdminAuthInterceptor adminAuthInterceptor, IpRateLimitInterceptor ipRateLimitInterceptor) {
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.ipRateLimitInterceptor = ipRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ipRateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/ping");

        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/auth/login");
    }
}
