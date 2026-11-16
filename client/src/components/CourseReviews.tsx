import { Alert, Pagination } from '@arco-design/web-react';
import { IconStarFill } from '@arco-design/web-react/icon';
import { useState } from 'react';

import { formatRating } from '../lib/catalogue';
import { formatDate } from '../i18n/format';
import { fr } from '../i18n/fr';
import { useCourseReviews } from '../hooks/catalogue';

export const REVIEWS_PAGE_SIZE = 10;

export function CourseReviews({ courseId }: { courseId: number }) {
  const [page, setPage] = useState(1);
  const query = useCourseReviews(courseId, page - 1, REVIEWS_PAGE_SIZE);
  const data = query.data;

  return (
    <section className="sanaa-reviews" aria-labelledby="course-reviews-title">
      <div className="sanaa-section-head">
        <h2 id="course-reviews-title" className="sanaa-title-lg">
          {fr.coursePage.reviewsTitle}
        </h2>
        {data && data.ratingCount > 0 ? (
          <p className="sanaa-reviews__summary">
            <span className="sanaa-reviews__average">
              <IconStarFill aria-hidden="true" /> {formatRating(data.ratingAvg)}
            </span>
            <span className="sanaa-caption">
              {fr.coursePage.reviewsCount(data.ratingCount)}
            </span>
          </p>
        ) : null}
      </div>

      {query.isError ? (
        <Alert
          type="warning"
          content={fr.coursePage.reviewsLoadError}
          action={
            <button type="button" className="sanaa-link" onClick={() => void query.refetch()}>
              {fr.common.retry}
            </button>
          }
        />
      ) : query.isPending ? (
        <p className="sanaa-text sanaa-muted">{fr.common.loading}</p>
      ) : !data || data.items.length === 0 ? (
        <p className="sanaa-text sanaa-muted">{fr.coursePage.reviewsEmpty}</p>
      ) : (
        <>
          <ul className="sanaa-review-list">
            {data.items.map((review) => (
              <li key={review.id} className="sanaa-review">
                <div className="sanaa-review__head">
                  <span className="sanaa-review__author">{review.learnerName}</span>
                  <span className="sanaa-review__rating">
                    <IconStarFill aria-hidden="true" />
                    {formatRating(review.rating)} / 5
                  </span>
                  <span className="sanaa-caption">{formatDate(review.createdAt)}</span>
                </div>
                {review.comment ? (
                  <p className="sanaa-text sanaa-review__comment">{review.comment}</p>
                ) : null}
                {review.publisherReply ? (
                  <div className="sanaa-review__reply">
                    <p className="sanaa-caption">{fr.coursePage.publisherReply}</p>
                    <p className="sanaa-text">{review.publisherReply}</p>
                  </div>
                ) : null}
              </li>
            ))}
          </ul>

          {data.totalPages > 1 ? (
            <div className="sanaa-pagination">
              <Pagination
                current={page}
                pageSize={REVIEWS_PAGE_SIZE}
                total={data.totalElements}
                onChange={(nextPage) => setPage(nextPage)}
                showTotal
              />
            </div>
          ) : null}
        </>
      )}
    </section>
  );
}
