package com.is2c.hrm.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionTemplate;

import com.is2c.hrm.approval.RecordingHandler;
import com.is2c.hrm.approval.api.ApprovalSubmission;
import com.is2c.hrm.approval.service.ApprovalService;
import com.is2c.hrm.notification.api.Notification;
import com.is2c.hrm.notification.api.NotificationChannel;
import com.is2c.hrm.notification.service.NotificationService;
import com.is2c.hrm.security.api.ScopeType;
import com.is2c.hrm.security.domain.AppUser;
import com.is2c.hrm.security.domain.AppUserRepository;
import com.is2c.testsupport.ApiTestBase;
import com.is2c.testsupport.OrgFixtures.Structure;

@Import(NotificationTest.Config.class)
class NotificationTest extends ApiTestBase {

    static class RecordingChannel implements NotificationChannel {
        final List<Notification> sent = new CopyOnWriteArrayList<>();
        volatile boolean fail;

        @Override
        public String name() {
            return "TEST";
        }

        @Override
        public void send(Notification n) {
            if (fail) {
                throw new IllegalStateException("SMTP down");
            }
            sent.add(n);
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        RecordingChannel recordingChannel() {
            return new RecordingChannel();
        }

        @Bean
        RecordingHandler recordingHandler() {
            return new RecordingHandler();
        }
    }

    @Autowired
    RecordingChannel channel;

    @Autowired
    ApprovalService approvals;

    @Autowired
    NotificationService notifications;

    @Autowired
    AppUserRepository users;

    @Autowired
    TransactionTemplate tx;

    Structure acme;
    AppUser staffUser;
    AppUser managerUser;
    UUID staffEmp;

    @BeforeEach
    void setUp() throws Exception {
        channel.sent.clear();
        channel.fail = false;
        String admin = data.bearerFor("root", "SUPER_ADMIN");
        acme = orgs.structure(admin, "ACME");
        String hr = data.bearerFor("hr", "HR_MANAGER");
        UUID managerEmp = orgs.employee(hr, acme, "Mana", "Ger");
        Map<String, Object> body = orgs.employeeBody(acme, "Staff", "Member");
        body.put("reportingManagerId", managerEmp);
        staffEmp = orgs.employee(hr, body);
        managerUser = withEmail(data.linkEmployee(data.user("manager", ScopeType.OWN, Set.of(), "EMPLOYEE"), managerEmp), "mgr@example.com");
        staffUser = withEmail(data.linkEmployee(data.user("staff", ScopeType.OWN, Set.of(), "EMPLOYEE"), staffEmp), "staff@example.com");
        postAs(admin, "/api/v1/approval/workflows", Map.of("companyId", acme.companyId(), "txnType", "TEST",
                "levels", List.of(Map.of("approverType", "REPORTING_MANAGER")))).andExpect(status().isCreated());
    }

    private AppUser withEmail(AppUser u, String email) {
        return tx.execute(s -> {
            AppUser x = users.findById(u.getId()).orElseThrow();
            x.setEmail(email);
            return x;
        });
    }

    private UUID submitCommitted() {
        return tx.execute(s -> data.runAs(staffUser, () -> approvals.submit(new ApprovalSubmission("TEST",
                UUID.randomUUID(), acme.companyId(), staffEmp, "Annual leave 2 days")).transactionId()));
    }

    private void await(int count) throws InterruptedException {
        for (int i = 0; i < 100 && channel.sent.size() < count; i++) {
            Thread.sleep(50);
        }
    }

    @Test
    @DisplayName("FR-074 approvers are notified after the submission commits; requester on completion")
    void approvalNotifications() throws Exception {
        UUID id = submitCommitted();
        await(1);
        assertThat(channel.sent).hasSize(1);
        assertThat(channel.sent.get(0).recipients()).containsExactly("mgr@example.com");
        assertThat(channel.sent.get(0).subject()).contains("Approval requested").contains("Annual leave");

        postAs(data.bearer(managerUser), "/api/v1/approval/transactions/{id}/approve", Map.of(), id).andExpect(status().isOk());
        await(2);
        assertThat(channel.sent.get(1).recipients()).containsExactly("staff@example.com");
        assertThat(channel.sent.get(1).subject()).contains("approved");
    }

    @Test
    @DisplayName("FR-074 nothing is sent when the business transaction rolls back")
    void noSendOnRollback() throws Exception {
        try {
            tx.executeWithoutResult(s -> {
                data.runAs(staffUser, () -> approvals.submit(new ApprovalSubmission("TEST", UUID.randomUUID(),
                        acme.companyId(), staffEmp, "rolled back")));
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            // rolled back
        }
        Thread.sleep(300);
        assertThat(channel.sent).isEmpty();
    }

    @Test
    @DisplayName("FR-074 delivery failures do not fail the business operation")
    void failureIsolated() throws Exception {
        channel.fail = true;
        UUID id = submitCommitted();
        Thread.sleep(200);
        postAs(data.bearer(managerUser), "/api/v1/approval/transactions/{id}/approve", Map.of(), id).andExpect(status().isOk());
        assertThat(notifications.deliver(Notification.of(List.of("x@example.com"), "s", "b"))).isFalse();
    }
}
