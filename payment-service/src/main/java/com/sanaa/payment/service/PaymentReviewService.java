package com.sanaa.payment.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.common.event.EventType;
import com.sanaa.payment.domain.Payment;
import com.sanaa.payment.domain.PaymentStatus;
import com.sanaa.payment.error.ConflictException;
import com.sanaa.payment.error.NotFoundException;
import com.sanaa.payment.repository.PaymentRepository;
import com.sanaa.payment.security.CurrentUser;
import com.sanaa.payment.web.dto.PaymentResponse;

/**
 * Review transitions. Only a payment still pending review can move, and the optimistic version is
 * the final guard against two administrators acting at the same time: the losing transaction
 * fails with {@code ObjectOptimisticLockingFailureException}, which the handler maps to 409.
 */
@Service
public class PaymentReviewService {

    private final PaymentRepository paymentRepository;
    private final OutboxWriter outboxWriter;

    public PaymentReviewService(PaymentRepository paymentRepository, OutboxWriter outboxWriter) {
        this.paymentRepository = paymentRepository;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public PaymentResponse approve(CurrentUser reviewer, long paymentId) {
        Payment payment = loadForReview(paymentId);
        payment.approve(reviewer.userId(), Instant.now());
        paymentRepository.saveAndFlush(payment);
        outboxWriter.append(payment, EventType.PAYMENT_APPROVED, null);
        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse reject(CurrentUser reviewer, long paymentId, String reason) {
        Payment payment = loadForReview(paymentId);
        payment.reject(reviewer.userId(), Instant.now(), reason);
        paymentRepository.saveAndFlush(payment);
        outboxWriter.append(payment, EventType.PAYMENT_REJECTED, reason);
        return PaymentResponse.from(payment);
    }

    private Payment loadForReview(long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found."));
        if (payment.getStatus() != PaymentStatus.PENDING_REVIEW) {
            throw new ConflictException("Only payments pending review can be reviewed.");
        }
        return payment;
    }
}
