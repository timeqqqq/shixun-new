package com.campus.qa.config;

import com.campus.qa.interceptor.IpRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final IpRateLimitInterceptor ipRateLimitInterceptor;

    public WebMvcConfig(IpRateLimitInterceptor ipRateLimitInterceptor) {
        this.ipRateLimitInterceptor = ipRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ipRateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/ping");
    }
}
