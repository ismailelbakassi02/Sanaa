package com.sanaa.payment.service;

import java.io.IOException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.sanaa.common.event.EventType;
import com.sanaa.payment.client.CourseServiceClient;
import com.sanaa.payment.client.CourseSnapshot;
import com.sanaa.payment.domain.Payment;
import com.sanaa.payment.domain.PaymentMethod;
import com.sanaa.payment.error.BadRequestException;
import com.sanaa.payment.error.ConflictException;
import com.sanaa.payment.error.PayloadTooLargeException;
import com.sanaa.payment.error.UnprocessableEntityException;
import com.sanaa.payment.receipt.ReceiptInspector;
import com.sanaa.payment.receipt.ReceiptStorage;
import com.sanaa.payment.repository.PaymentRepository;
import com.sanaa.payment.security.CurrentUser;
import com.sanaa.payment.web.dto.PaymentResponse;

/**
 * Creates a payment from a receipt upload.
 *
 * <p>The client only chooses the course and the method. Amount, currency and course title are read
 * from course-service, and the receipt type is detected from its bytes, never from the declared
 * Content-Type or the filename.
 */
@Service
public class PaymentSubmissionService {

    private final PaymentRepository paymentRepository;
    private final CourseServiceClient courseServiceClient;
    private final ReceiptStorage receiptStorage;
    private final OutboxWriter outboxWriter;

    public PaymentSubmissionService(PaymentRepository paymentRepository,
                                    CourseServiceClient courseServiceClient,
                                    ReceiptStorage receiptStorage,
                                    OutboxWriter outboxWriter) {
        this.paymentRepository = paymentRepository;
        this.courseServiceClient = courseServiceClient;
        this.receiptStorage = receiptStorage;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public PaymentResponse submit(CurrentUser user, long courseId, PaymentMethod method,
                                  MultipartFile receipt, String authorization) {
        byte[] content = readReceipt(receipt);
        String contentType = ReceiptInspector.detectContentType(content);
        if (contentType == null) {
            throw new UnprocessableEntityException(
                    "The receipt must be a JPEG, PNG or PDF file.");
        }

        CourseSnapshot course = courseServiceClient.fetchPublishedCourse(courseId, authorization);
        if (course.price() == null || course.price().signum() <= 0) {
            throw new UnprocessableEntityException("Free courses do not need a payment.");
        }
        if (courseServiceClient.hasActiveEnrollment(courseId, authorization)) {
            throw new ConflictException("You are already enrolled in this course.");
        }

        // File first: the row must never reference a receipt that was not written. The catch below
        // removes the file when the insert (or the outbox insert) fails and the transaction rolls
        // back, so a failed submission leaves no orphan.
        String receiptPath = receiptStorage.store(user.tenantId(), content);
        try {
            long resolvedCourseId = course.id() == null ? courseId : course.id();
            Payment payment = new Payment(
                    user.userId(),
                    user.email(),
                    user.name(),
                    resolvedCourseId,
                    course.title(),
                    course.price(),
                    course.currency(),
                    method,
                    receiptPath,
                    contentType);
            // Flush so the id exists for the event and so a unique-index conflict is raised here,
            // where the file can still be removed.
            paymentRepository.saveAndFlush(payment);
            outboxWriter.append(payment, EventType.PAYMENT_SUBMITTED, null);
            return PaymentResponse.from(payment);
        } catch (RuntimeException exception) {
            receiptStorage.delete(receiptPath);
            throw exception;
        }
    }

    private static byte[] readReceipt(MultipartFile receipt) {
        if (receipt == null || receipt.isEmpty()) {
            throw new UnprocessableEntityException("The receipt file is empty.");
        }
        if (receipt.getSize() > ReceiptInspector.MAX_SIZE_BYTES) {
            throw new PayloadTooLargeException("The receipt must not exceed 5 MB.");
        }
        byte[] content;
        try {
            content = receipt.getBytes();
        } catch (IOException exception) {
            throw new BadRequestException("The receipt could not be read.");
        }
        if (content.length == 0) {
            throw new UnprocessableEntityException("The receipt file is empty.");
        }
        if (content.length > ReceiptInspector.MAX_SIZE_BYTES) {
            throw new PayloadTooLargeException("The receipt must not exceed 5 MB.");
        }
        return content;
    }
}
