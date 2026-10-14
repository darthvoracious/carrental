package com.carrental.config;

import com.carrental.domain.CarType;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "carrental")
public record CarRentalProperties(String dataFile, int maxRentalDays, Map<CarType, Integer> fleet) {
}
