package com.is2c.hrm.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import com.is2c.hrm.notification.api.Notification;
import com.is2c.hrm.notification.api.NotificationChannel;

@Import(PayslipEmailApiTest.Config.class)
@TestPropertySource(properties = { "hrm.mail.enabled=true", "spring.mail.host=localhost" })
class PayslipEmailApiTest extends FinalizedPayrollTestBase {

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
        RecordingChannel payslipRecordingChannel() {
            return new RecordingChannel();
        }
    }

    @Autowired
    RecordingChannel channel;

    @Test
    @DisplayName("FR-088 payslips are emailed as PDF attachments with per-recipient results")
    void email() throws Exception {
        channel.sent.clear();
        channel.fail = false;
        postAs(po, "/api/v1/payroll/periods/{p}/payslips/email", Map.of(), march).andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(1)).andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.results[?(@.employeeId == '" + e1 + "')].status").value("SENT"))
                .andExpect(jsonPath("$.results[?(@.employeeId == '" + e2 + "')].status").value("NO_EMAIL"));
        assertThat(channel.sent).hasSize(1);
        Notification n = channel.sent.get(0);
        assertThat(n.recipients()).containsExactly("one@example.com");
        assertThat(n.attachments()).hasSize(1);
        assertThat(n.attachments().get(0).contentType()).isEqualTo("application/pdf");
        PdfReader reader = new PdfReader(n.attachments().get(0).content());
        assertThat(new PdfTextExtractor(reader).getTextFromPage(1)).contains(resultOf(e1).get("payslipNo").asString());
        reader.close();
    }

    @Test
    @DisplayName("FR-088 delivery failures are reported per recipient; only selected employees are mailed")
    void failures() throws Exception {
        channel.sent.clear();
        channel.fail = true;
        postAs(po, "/api/v1/payroll/periods/{p}/payslips/email", Map.of("employeeIds", List.of(e1)), march)
                .andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].status").value("FAILED"));
        channel.fail = false;
        postAs(data.bearerFor("ca", "COMPANY_ADMIN"), "/api/v1/payroll/periods/{p}/payslips/email", Map.of(), march)
                .andExpect(status().isForbidden());
    }
}
