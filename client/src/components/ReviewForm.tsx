import { Alert, Button, Input, Message, Rate } from '@arco-design/web-react';
import { useEffect, useState } from 'react';

import { messageForError } from '../api/messages';
import { useSaveReview, useMyReview } from '../hooks/learner';
import { fr } from '../i18n/fr';

export const REVIEW_COMMENT_MAX = 2000;

interface ReviewFormProps {
  courseId: number;
  /** Reviews require an active enrollment; the page owns that trusted state. */
  canReview: boolean;
}

/**
 * Create or edit the caller's review. The form is prefilled from my-review,
 * never from the public review list: only the author may see a hidden review.
 */
export function ReviewForm({ courseId, canReview }: ReviewFormProps) {
  const query = useMyReview(courseId, canReview);
  const existing = query.data ?? null;
  const isUpdate = existing !== null;
  const mutation = useSaveReview(courseId, isUpdate);
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (existing) {
      setRating(existing.rating);
      setComment(existing.comment ?? '');
    }
  }, [existing]);

  if (!canReview) {
    return null;
  }

  const submit = () => {
    if (rating < 1 || rating > 5) {
      setError(fr.coursePage.review.ratingRequired);
      return;
    }
    const trimmed = comment.trim();
    if (trimmed.length > REVIEW_COMMENT_MAX) {
      setError(fr.coursePage.review.tooLong);
      return;
    }
    setError(null);
    mutation.mutate(
      { rating, comment: trimmed.length > 0 ? trimmed : null },
      {
        onSuccess: (saved) => {
          setRating(saved.rating);
          setComment(saved.comment ?? '');
          Message.success(
            isUpdate ? fr.coursePage.review.updated : fr.coursePage.review.success,
          );
        },
        onError: (failure) => {
          Message.error(messageForError(failure));
        },
      },
    );
  };

  return (
    <section className="sanaa-review-form" aria-labelledby="review-form-title">
      <h3 id="review-form-title" className="sanaa-title-md">
        {isUpdate ? fr.coursePage.review.editTitle : fr.coursePage.review.title}
      </h3>

      {query.isError ? (
        <p className="sanaa-field-error">{fr.coursePage.review.loadError}</p>
      ) : null}

      {existing?.status === 'HIDDEN' ? (
        <Alert
          type="warning"
          className="sanaa-alert"
          content={fr.coursePage.review.hidden}
        />
      ) : null}

      {existing?.publisherReply ? (
        <div className="sanaa-review__reply">
          <p className="sanaa-caption">{fr.coursePage.publisherReply}</p>
          <p className="sanaa-text">{existing.publisherReply}</p>
        </div>
      ) : null}

      <div className="sanaa-field">
        <span className="sanaa-field__label">{fr.coursePage.review.ratingLabel}</span>
        <Rate
          value={rating}
          onChange={(value) => {
            setRating(value);
            setError(null);
          }}
          allowClear
          aria-label={fr.coursePage.review.ratingLabel}
        />
      </div>

      <div className="sanaa-field">
        <label className="sanaa-field__label" htmlFor="review-comment">
          {fr.coursePage.review.commentLabel}{' '}
          <span className="sanaa-caption">{fr.common.optional}</span>
        </label>
        <Input.TextArea
          id="review-comment"
          value={comment}
          onChange={(value) => setComment(value)}
          placeholder={fr.coursePage.review.commentPlaceholder}
          maxLength={REVIEW_COMMENT_MAX}
          autoSize={{ minRows: 4, maxRows: 8 }}
        />
        <span className="sanaa-caption">
          {fr.coursePage.review.counter(comment.length)}
        </span>
      </div>

      {error ? <p className="sanaa-field-error">{error}</p> : null}

      <Button type="primary" loading={mutation.isPending} onClick={submit}>
        {mutation.isPending
          ? fr.coursePage.review.submitting
          : isUpdate
            ? fr.coursePage.review.update
            : fr.coursePage.review.submit}
      </Button>
    </section>
  );
}
