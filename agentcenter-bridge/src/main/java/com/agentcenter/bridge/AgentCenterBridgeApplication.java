package com.agentcenter.bridge;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.agentcenter.bridge.infrastructure.persistence.mapper")
public class AgentCenterBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentCenterBridgeApplication.class, args);
    }
}
