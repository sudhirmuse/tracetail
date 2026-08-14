/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import dev.sudhirmuse.tracetail.RemoteSourceDialog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RemoteSourceServiceTest {
    private final RemoteSourceService service = new RemoteSourceService();

    @Test void buildsDockerFollowCommand() {
        List<String> command = service.command(request(RemoteSourceDialog.Type.DOCKER, "payments-api", "ssh://server"));
        assertEquals(List.of("docker", "--host", "ssh://server", "logs", "--tail", "10000", "--follow", "payments-api"), command);
    }

    @Test void buildsKubernetesCommandWithContextAndContainer() {
        RemoteSourceDialog.Request request = new RemoteSourceDialog.Request(RemoteSourceDialog.Type.KUBERNETES,
            "payments-abc", "", 22, "", "", "prod", "api", "company-cluster", true, false);
        List<String> command = service.command(request);
        assertTrue(command.containsAll(List.of("--context", "company-cluster", "--namespace", "prod", "--container", "api", "--follow")));
    }

    @Test void rejectsUnsafeSshPath() {
        RemoteSourceDialog.Request request = new RemoteSourceDialog.Request(RemoteSourceDialog.Type.SSH,
            "/var/log/app'; rm -rf /", "server", 22, "sudhir", "", "", "", "", true, false);
        assertThrows(IllegalArgumentException.class, () -> service.command(request));
    }

    @Test void buildsCloudWatchFollowCommand() {
        List<String> command = service.command(request(RemoteSourceDialog.Type.CLOUDWATCH, "/aws/lambda/payments", "eu-west-1"));
        assertEquals(List.of("aws", "logs", "tail", "/aws/lambda/payments", "--format", "detailed", "--region", "eu-west-1", "--follow"), command);
    }

    @Test void sshUsesConfiguredPortAndKeyWithoutDownloadingWholeFile() {
        RemoteSourceDialog.Request request = new RemoteSourceDialog.Request(RemoteSourceDialog.Type.SSH,
            "/var/log/payments.log", "logs.example.com", 2222, "sudhir", "C:\\keys\\id_ed25519", "", "", "", true, false);
        List<String> command = service.command(request);
        assertTrue(command.containsAll(List.of("-p", "2222", "-i", "C:\\keys\\id_ed25519", "sudhir@logs.example.com")));
        assertEquals("tail -n 10000 -F -- '/var/log/payments.log'", command.getLast());
    }

    @Test void kubernetesPreviousContainerDoesNotFollow() {
        RemoteSourceDialog.Request request = new RemoteSourceDialog.Request(RemoteSourceDialog.Type.KUBERNETES,
            "payments-abc", "", 22, "", "", "prod", "api", "company-cluster", true, true);
        List<String> command = service.command(request);
        assertTrue(command.contains("--previous"));
        assertTrue(!command.contains("--follow"));
    }

    private static RemoteSourceDialog.Request request(RemoteSourceDialog.Type type, String location, String host) {
        return new RemoteSourceDialog.Request(type, location, host, 22, "", "", "default", "", "", true, false);
    }
}
