package com.journalflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.journalflow.mapper")
public class JournalFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(JournalFlowApplication.class, args);
    }
}
