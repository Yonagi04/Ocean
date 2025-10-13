package com.yonagi.ocean.spi;

import com.alibaba.nacos.api.config.ConfigService;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/12 15:57
 */
@FunctionalInterface
public interface ConfigRecoveryAction {

    void recover(ConfigService configService);
}
