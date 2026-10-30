package com.enterprise.bts;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BtsApplication {

    public static void main(String[] args) {
        // All timestamps are UTC end to end (JVM, JDBC session, database).
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(BtsApplication.class, args);
    }
}
