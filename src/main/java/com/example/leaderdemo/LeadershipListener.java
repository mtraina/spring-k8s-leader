package com.example.leaderdemo;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.cloud.kubernetes.commons.leader.event.OnGrantedEvent;
import org.springframework.cloud.kubernetes.commons.leader.event.OnRevokedEvent;

@Component
public class LeadershipListener {

    @EventListener
    public void onGranted(OnGrantedEvent event) {
        System.out.println("💡 I AM THE LEADER: " + event.getRole());
    }

    @EventListener
    public void onRevoked(OnRevokedEvent event) {
        System.out.println("❌ Leadership revoked: " + event.getRole());
    }
}
