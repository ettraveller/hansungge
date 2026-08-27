package com.hanseongge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 한성금거래소 쇼핑몰 API 진입점.
 *
 * <p>스케줄링을 켜두는 이유: 시세 자동 수집(STEP 7)과 결제 정합성 점검 배치(STEP 13)가
 * 스케줄러 기반이다.
 */
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
public class HanseonggeApplication {

    public static void main(String[] args) {
        SpringApplication.run(HanseonggeApplication.class, args);
    }
}
