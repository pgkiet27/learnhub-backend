package com.learnhub.notification.service;

import com.learnhub.common.event.ChurnRiskDetectedEvent;
import com.learnhub.notification.client.UserDirectoryClient;
import com.learnhub.notification.client.UserDirectoryClient.UserSummary;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChurnReminderService {

    private final UserDirectoryClient userDirectoryClient;
    private final JavaMailSender mailSender;

    @Value("${notification.mail.from}")
    private String from;

    @Value("${notification.frontend-url}")
    private String frontendUrl;

    /** Returns false when the email was intentionally not sent (opted out, unknown address). */
    public boolean sendReminder(ChurnRiskDetectedEvent event) throws MessagingException {
        Optional<UserSummary> summary = userDirectoryClient.findSummary(event.getUserId());
        if (summary.isPresent() && !summary.get().emailLearningReminder()) {
            log.info("User {} turned off learning reminders, skipping", event.getUserId());
            return false;
        }
        Optional<String> email = userDirectoryClient.findEmail(event.getUserId());
        if (email.isEmpty()) {
            log.warn("No email found for user {}, skipping reminder", event.getUserId());
            return false;
        }

        String name = summary.map(UserSummary::fullName).filter(n -> !n.isBlank()).orElse("bạn");
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
        helper.setFrom(from);
        helper.setTo(email.get());
        helper.setSubject("Tiếp tục khóa học \"" + event.getCourseTitle() + "\" nhé!");
        helper.setText(buildHtml(name, event), true);
        mailSender.send(message);

        log.info("Sent churn reminder for enrollment {} (score {})", event.getEnrollmentId(), event.getChurnScore());
        return true;
    }

    String buildHtml(String name, ChurnRiskDetectedEvent event) {
        String course = HtmlUtils.htmlEscape(event.getCourseTitle());
        String progress = event.getProgressPercent() == null ? "0"
                : event.getProgressPercent().setScale(0, RoundingMode.HALF_UP).toPlainString();
        String link = frontendUrl + "/my-learning/" + event.getCourseId() + "/start";

        String status;
        if (event.getLastAccessedAt() == null) {
            status = "Bạn đã đăng ký khóa <b>" + course + "</b> nhưng chưa bắt đầu bài học nào.";
        } else {
            long days = Math.max(1, Duration.between(event.getLastAccessedAt(), Instant.now()).toDays());
            status = "Đã " + days + " ngày bạn chưa quay lại khóa <b>" + course + "</b>. "
                    + "Bạn đã hoàn thành <b>" + progress + "%</b> khóa học rồi đấy!";
        }

        return """
                <div style="font-family:Arial,sans-serif;max-width:560px;margin:auto;color:#0f172a">
                  <h2 style="color:#0d9488">Chào %s,</h2>
                  <p>%s</p>
                  <p>Chỉ cần 15 phút mỗi ngày là bạn sẽ sớm hoàn thành mục tiêu của mình.</p>
                  <p style="margin:28px 0">
                    <a href="%s" style="background:#0d9488;color:#fff;padding:12px 22px;border-radius:8px;text-decoration:none">
                      Tiếp tục học ngay
                    </a>
                  </p>
                  <p style="font-size:12px;color:#94a3b8">
                    Bạn nhận email này vì đang bật "Nhắc nhở học tập" trong cài đặt thông báo LearnHub.
                  </p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(name), status, link);
    }
}
