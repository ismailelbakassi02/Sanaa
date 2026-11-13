import { http } from './http';

/**
 * Typed mirror of payment-service/web/dto. Keep this file in sync with the
 * Java records: the payload shapes are the contract, not a guess.
 */

export type PaymentStatus = 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED';
export type PaymentMethod = 'BANK_WIRE' | 'CASHPLUS';

/** Mirrors PaymentResponse from payment-service. */
export interface Payment {
  id: number;
  userId: number;
  userEmail: string;
  userName: string;
  courseId: number;
  courseTitle: string;
  /** BigDecimal serialized by Jackson as a JSON number. */
  amount: number;
  currency: string;
  method: PaymentMethod;
  status: PaymentStatus;
  receiptContentType: string;
  reviewedBy: number | null;
  reviewedAt: string | null;
  rejectionReason: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors PageResponse<PaymentResponse> from payment-service. */
export interface PaymentPage {
  items: Payment[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PaymentPageParams {
  /** Zero-based, as the backend expects. */
  page: number;
  size: number;
}

export interface AdminPaymentParams extends PaymentPageParams {
  /** Omit to list every status. */
  status?: PaymentStatus;
}

export interface SubmitPaymentInput {
  courseId: number;
  method: PaymentMethod;
  receipt: File;
}

const MAX_PAGES = 20;

export async function submitPayment(input: SubmitPaymentInput): Promise<Payment> {
  const formData = new FormData();
  formData.append('receipt', input.receipt, input.receipt.name);
  // courseId and method are @RequestParam on the server, so they travel in the
  // query string while the body stays a real multipart request for the file.
  const response = await http.post<Payment>('/api/payments', formData, {
    params: { courseId: input.courseId, method: input.method },
  });
  return response.data;
}

export async function fetchMyPayments(params: PaymentPageParams): Promise<PaymentPage> {
  const response = await http.get<PaymentPage>('/api/payments/me', { params });
  return response.data;
}

/**
 * Reads every page of the caller's payments.
 *
 * WHY: the API has no filter by course, so the course detail page has to scan
 * the student's history to find an open payment. The set is small, and the
 * page count is capped to keep a broken cursor from looping forever.
 */
export async function fetchAllMyPayments(pageSize = 50): Promise<Payment[]> {
  const first = await fetchMyPayments({ page: 0, size: pageSize });
  const items = [...first.items];
  const lastPage = Math.min(first.totalPages, MAX_PAGES);
  for (let page = 1; page < lastPage; page += 1) {
    const next = await fetchMyPayments({ page, size: pageSize });
    items.push(...next.items);
  }
  return items;
}

export async function fetchAdminPayments(
  params: AdminPaymentParams,
): Promise<PaymentPage> {
  const response = await http.get<PaymentPage>('/api/payments', { params });
  return response.data;
}

export async function approvePayment(id: number): Promise<Payment> {
  const response = await http.post<Payment>(`/api/payments/${id}/approve`);
  return response.data;
}

export async function rejectPayment(id: number, reason: string): Promise<Payment> {
  const response = await http.post<Payment>(`/api/payments/${id}/reject`, { reason });
  return response.data;
}

/**
 * The endpoint answers with Content-Disposition: attachment, so the browser
 * never navigates to it directly. Callers open the returned blob themselves.
 */
export async function fetchPaymentReceipt(id: number): Promise<Blob> {
  const response = await http.get<Blob>(`/api/payments/${id}/receipt`, {
    responseType: 'blob',
  });
  return response.data;
}
