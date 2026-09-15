package com.campuspilot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 后端应用入口，负责启动 Spring Boot 并开启事务代理。 */
//开启 Spring 的 AOP 代理功能。
@EnableAspectJAutoProxy(exposeProxy = true)
@EnableScheduling//开启 Spring 的定时任务功能。
@MapperScan("com.campuspilot.mapper")//扫描 com.campuspilot.mapper 包下面的 Mapper 接口，并把它们注册到 Spring 容器中。
@SpringBootApplication
public class CampusPilotApplication {

    /** 启动 Campus Pilot Spring Boot 应用。 */
    public static void main(String[] args) {
        SpringApplication.run(CampusPilotApplication.class, args);
    }

}
