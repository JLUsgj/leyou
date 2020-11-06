package com.leyou.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @author SGJ
 * @description 过滤器属性
 * @date 2020/6/12
 */
@ConfigurationProperties(prefix = "leyou.filter")
public class FilterProperties {
    private List<String> allowPaths;

    public List<String> getAllowPaths() {
        return allowPaths;
    }

    public void setAllowPaths(List<String> allowPaths) {
        this.allowPaths = allowPaths;
    }
}
