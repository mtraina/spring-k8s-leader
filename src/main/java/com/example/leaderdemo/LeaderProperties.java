package com.example.leaderdemo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LeaderProperties {

    @Value("${leader.lock-name:spring-k8s-leader-lock}")
    private String lockName;

    @Value("${leader.namespace:default}")
    private String namespace;

    @Value("${leader.identity:#{T(java.util.UUID).randomUUID().toString()}}")
    private String identity;

    @Value("${leader.lease-duration-seconds:15}")
    private int leaseDurationSeconds;

    @Value("${leader.renew-interval-seconds:5}")
    private int renewIntervalSeconds;

    public String getLockName() {
        return lockName;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getIdentity() {
        return identity;
    }

    public int getLeaseDurationSeconds() {
        return leaseDurationSeconds;
    }

    public int getRenewIntervalSeconds() {
        return renewIntervalSeconds;
    }
}