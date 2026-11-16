import { Card, Tag } from '@arco-design/web-react';
import { IconStarFill } from '@arco-design/web-react/icon';
import { Link } from 'react-router-dom';

import type { PublicCourseCard } from '../api/catalogue';
import { formatPrice } from '../i18n/format';
import { fr } from '../i18n/fr';
import { priceView, ratingLabel } from '../lib/catalogue';
import { CourseCover } from './CourseCover';

interface PriceBlockProps {
  course: Pick<PublicCourseCard, 'price' | 'effectivePrice' | 'currency' | 'discountActive'>;
  size?: 'card' | 'detail';
  freeLabel?: boolean;
}

/**
 * The price block. It trusts `discountActive` from the server instead of
 * comparing the two amounts, so an expired promotion never shows a false
 * strikethrough. Only the effective amount is announced, the old price is
 * marked as decoration for assistive technology.
 */
export function PriceBlock({ course, size = 'card', freeLabel = true }: PriceBlockProps) {
  const view = priceView(course);
  if (view.kind === 'free') {
    return (
      <span className={`sanaa-price sanaa-price--${size} sanaa-price--free`}>
        {freeLabel ? fr.marketplace.free : formatPrice(0, view.currency)}
      </span>
    );
  }

  return (
    <span className={`sanaa-price-group sanaa-price-group--${size}`}>
      <span className="sanaa-price">{formatPrice(view.current, view.currency)}</span>
      {view.kind === 'discount' && view.original !== null ? (
        <>
          <span className="sanaa-price__original" aria-hidden="true">
            {formatPrice(view.original, view.currency)}
          </span>
          <Tag color="red" size="small">
            {fr.marketplace.discountPercent(view.percent ?? 0)}
          </Tag>
        </>
      ) : null}
    </span>
  );
}

export function CourseCard({ course }: { course: PublicCourseCard }) {
  return (
    <Link
      to={`/courses/${course.id}`}
      className="sanaa-course-card-link"
    >
      <Card hoverable bordered className="sanaa-course-card" bodyStyle={{ padding: 0 }}>
        <CourseCover title={course.title} />
        <div className="sanaa-course-card__body">
          <div className="sanaa-course-card__meta">
            <span className="sanaa-course-card__publisher">{course.publisherName}</span>
            <Tag size="small" color={course.publisherType === 'ORGANIZATION' ? 'arcoblue' : 'green'}>
              {fr.publisherTypes[course.publisherType]}
            </Tag>
          </div>
          <h3 className="sanaa-course-card__title">{course.title}</h3>
          <p className="sanaa-course-card__instructor">
            {fr.marketplace.byInstructor(course.instructorName)}
          </p>
          <p className="sanaa-course-card__rating">
            <IconStarFill aria-hidden="true" />
            <span>{ratingLabel(course.ratingAvg, course.ratingCount)}</span>
          </p>
          <div className="sanaa-course-card__footer">
            <span className="sanaa-course-card__level">{fr.levels[course.level]}</span>
            <PriceBlock course={course} />
          </div>
        </div>
      </Card>
    </Link>
  );
}
