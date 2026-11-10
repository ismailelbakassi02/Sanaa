package com.sanaa.payment.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.common.security.Role;
import com.sanaa.payment.domain.Payment;
import com.sanaa.payment.domain.PaymentStatus;
import com.sanaa.payment.error.NotFoundException;
import com.sanaa.payment.receipt.ReceiptStorage;
import com.sanaa.payment.repository.PaymentRepository;
import com.sanaa.payment.security.CurrentUser;
import com.sanaa.payment.web.dto.PageResponse;
import com.sanaa.payment.web.dto.PaymentResponse;

/**
 * Read paths. Every query is tenant-filtered by Hibernate through {@code @TenantId}, so a payment
 * of another tenant behaves exactly like a missing one.
 */
@Service
public class PaymentQueryService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    /** Newest first, with the id as a stable tie-breaker for equal timestamps. */
    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final PaymentRepository paymentRepository;
    private final ReceiptStorage receiptStorage;

    public PaymentQueryService(PaymentRepository paymentRepository, ReceiptStorage receiptStorage) {
        this.paymentRepository = paymentRepository;
        this.receiptStorage = receiptStorage;
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> listMine(CurrentUser user, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = normalizeSize(size);
        Pageable pageable = PageRequest.of(safePage, safeSize, NEWEST_FIRST);

        Page<Payment> payments = paymentRepository.findByUserId(user.userId(), pageable);
        return toResponse(payments, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> list(CurrentUser user, PaymentStatus status,
                                              int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = normalizeSize(size);
        Pageable pageable = PageRequest.of(safePage, safeSize, NEWEST_FIRST);

        Page<Payment> payments = status == null
                ? paymentRepository.findAll(pageable)
                : paymentRepository.findByStatus(status, pageable);
        return toResponse(payments, safePage, safeSize);
    }

    /**
     * Returns the receipt to its owner or to any tenant administrator of the same tenant. Any
     * other caller gets 404 so the existence of the payment is not disclosed.
     */
    @Transactional(readOnly = true)
    public ReceiptFile downloadReceipt(CurrentUser user, long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found."));
        if (user.role() == Role.STUDENT && !payment.getUserId().equals(user.userId())) {
            throw new NotFoundException("Payment not found.");
        }

        Path path = receiptStorage.resolve(payment.getReceiptPath());
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("Receipt not found.");
        }
        try {
            return new ReceiptFile(Files.readAllBytes(path), payment.getReceiptContentType());
        } catch (IOException exception) {
            throw new NotFoundException("Receipt not found.");
        }
    }

    private static PageResponse<PaymentResponse> toResponse(Page<Payment> page, int safePage,
                                                            int safeSize) {
        List<PaymentResponse> items = page.getContent().stream()
                .map(PaymentResponse::from)
                .toList();
        return new PageResponse<>(items, safePage, safeSize,
                page.getTotalElements(), page.getTotalPages());
    }

    private static int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
