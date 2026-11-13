import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import {
  approvePayment,
  fetchAdminPayments,
  fetchAllMyPayments,
  fetchMyPayments,
  fetchPaymentReceipt,
  rejectPayment,
  submitPayment,
  type AdminPaymentParams,
  type Payment,
  type PaymentStatus,
  type SubmitPaymentInput,
} from '../api/payments';
import { openBlobInNewTab } from '../lib/receipt';
import { useAuthStore } from '../store/auth';

export const PAYMENTS_PAGE_SIZE = 10;

export const paymentKeys = {
  /** Prefix shared by every payment query, used for invalidation. */
  all: ['payments'] as const,
  mine: (page: number) => ['payments', 'me', 'page', page] as const,
  mineAll: ['payments', 'me'] as const,
  open: (courseId: number) => ['payments', 'me', 'open', courseId] as const,
  admin: (status: PaymentStatus | 'ALL', page: number) =>
    ['payments', 'admin', status, page] as const,
  adminAll: ['payments', 'admin'] as const,
};

/** One paginated page of the student's payments, one-based in the UI. */
export function useMyPayments(page: number) {
  return useQuery({
    queryKey: paymentKeys.mine(page),
    queryFn: () => fetchMyPayments({ page: page - 1, size: PAYMENTS_PAGE_SIZE }),
    placeholderData: (previous) => previous,
  });
}

/**
 * The course detail page needs to know whether this student already has an
 * open payment. The API has no per-course filter, so the whole history is
 * scanned and the PENDING_REVIEW row is selected on the client.
 */
export function useMyOpenPayment(courseId: number | null) {
  const role = useAuthStore((state) => state.user?.role);
  return useQuery({
    queryKey: paymentKeys.open(courseId ?? 0),
    queryFn: () => fetchAllMyPayments(),
    enabled: role === 'STUDENT' && courseId !== null,
    select: (payments: Payment[]) =>
      payments.find(
        (payment) => payment.courseId === courseId && payment.status === 'PENDING_REVIEW',
      ) ?? null,
  });
}

export function useAdminPayments(status: PaymentStatus | 'ALL', page: number) {
  const params: AdminPaymentParams = {
    page: page - 1,
    size: PAYMENTS_PAGE_SIZE,
  };
  if (status !== 'ALL') {
    params.status = status;
  }
  return useQuery({
    queryKey: paymentKeys.admin(status, page),
    queryFn: () => fetchAdminPayments(params),
    placeholderData: (previous) => previous,
  });
}

export function useSubmitPayment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: SubmitPaymentInput) => submitPayment(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: paymentKeys.all });
    },
  });
}

export function useApprovePayment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => approvePayment(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: paymentKeys.all });
    },
    onError: () => {
      // A 409 means the row was processed elsewhere: refresh so the admin sees
      // the new status instead of a stale Approve button.
      void queryClient.invalidateQueries({ queryKey: paymentKeys.all });
    },
  });
}

export function useRejectPayment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) =>
      rejectPayment(id, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: paymentKeys.all });
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: paymentKeys.all });
    },
  });
}

/**
 * Downloads a receipt through the authenticated client and opens it in a new
 * tab. The object URL is revoked by openBlobInNewTab after the tab loads.
 */
export function usePaymentReceipt() {
  const [openingId, setOpeningId] = useState<number | null>(null);

  const open = async (paymentId: number): Promise<void> => {
    setOpeningId(paymentId);
    try {
      const blob = await fetchPaymentReceipt(paymentId);
      openBlobInNewTab(blob);
    } finally {
      setOpeningId(null);
    }
  };

  return { open, openingId };
}
