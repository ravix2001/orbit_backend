package com.ravi.orbit.scheduler;

import com.ravi.orbit.entity.Payment;
import com.ravi.orbit.enums.EPaymentMethod;
import com.ravi.orbit.enums.EPaymentStatus;
import com.ravi.orbit.repository.PaymentRepository;
import com.ravi.orbit.repository.RefreshTokenRepository;
import com.ravi.orbit.service.IEmailService;
import com.ravi.orbit.service.impl.EsewaPaymentServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@Transactional
@RequiredArgsConstructor
public class ScheduledTasks {

//    private final IEmailService emailService;

    private final RefreshTokenRepository refreshTokenRepository;

    private final PaymentRepository paymentRepository;
    private final EsewaPaymentServiceImpl esewaPaymentService;

//    public void sendEmailVerification() {
//
//    }
//
//    public void sendPasswordResetEmail() {
//
//    }
//
//    public void sendEmailReminder() {
//
//    }
//
//    public void sendEmailNotification() {
//
//    }

    @Scheduled(cron = "0 0 0 * * 1")
    public void clearExpiredSessions() {
        refreshTokenRepository.deleteByExpiryDateBefore(LocalDateTime.now());
        log.info("Expired sessions cleared");
    }

    /**
     * Runs every 60 seconds.
     *
     * It checks PENDING eSewa payments that did not receive
     * a browser callback.
     *
     * The scheduler checks all pending eSewa payments every 60 seconds.
     * That's perfectly reasonable for your current project, but once you have many transactions,
     * change it to query only payments older than ~5 minutes.
     */
    @Scheduled(fixedDelay = 60_000)
//    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void reconcilePendingPayments() {

        List<Payment> pendingPayments = paymentRepository.findAllByPaymentStatusAndPaymentMethod(
                                EPaymentStatus.PENDING,
                                EPaymentMethod.ESEWA
                        );

        for (Payment payment : pendingPayments) {

            try {
                esewaPaymentService.reconcilePendingPayment(payment);
            } catch (Exception e) {
                /*
                 * Do not stop reconciliation of other payments
                 * if one transaction fails.
                 */
                log.error("Failed to reconcile eSewa payment. " + "paymentId={}, transactionUuid={}",
                        payment.getId(),
                        payment.getTransactionUuid(),
                        e
                );
            }
        }
    }

}
