package com.eneik.generated.service;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class TimeService {
    private LocalDateTime fixedTime = null;

    public LocalDateTime getCurrentTime() {
        if (fixedTime != null) {
            return fixedTime;
        }
        return LocalDateTime.now();
    }

    public void setFixedTime(LocalDateTime fixedTime) {
        this.fixedTime = fixedTime;
    }

    public void clearFixedTime() {
        this.fixedTime = null;
    }
}
