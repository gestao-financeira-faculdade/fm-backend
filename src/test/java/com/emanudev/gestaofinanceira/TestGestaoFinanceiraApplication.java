package com.emanudev.gestaofinanceira;

import org.springframework.boot.SpringApplication;

public class TestGestaoFinanceiraApplication {

    public static void main(String[] args) {
        SpringApplication.from(GestaoFinanceiraApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
