package com.shifu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.shifu.mapper")
@SpringBootApplication
public class ShiFuApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShiFuApplication.class, args);
    }

}
