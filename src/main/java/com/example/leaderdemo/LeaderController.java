package com.example.leaderdemo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class LeaderController {

    private final ScheduledLeaderTask scheduledLeaderTask;
    private final LeaderProperties props;

    public LeaderController(ScheduledLeaderTask scheduledLeaderTask, LeaderProperties props) {
        this.scheduledLeaderTask = scheduledLeaderTask;
        this.props = props;
    }

    @GetMapping("/leader")
    public Map<String, Object> leader() {
        return Map.of(
                "leader", scheduledLeaderTask.getLeader(),
                "identity", props.getIdentity(),
                "lockName", props.getLockName(),
                "namespace", props.getNamespace(),
                "hostname", System.getenv("HOSTNAME")
        );
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
