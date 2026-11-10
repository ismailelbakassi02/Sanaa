import { Card } from '@arco-design/web-react';
import { Link } from 'react-router-dom';

import type { CourseSummary } from '../api/courses';
import { CourseCover } from './CourseCover';
import { CourseStatusTag } from './CourseStatusTag';
import { formatPrice } from '../i18n/format';

interface CourseCardProps {
  course: CourseSummary;
  /** Status is only shown to instructors and administrators. */
  showStatus: boolean;
  to: string;
}

export function CourseCard({ course, showStatus, to }: CourseCardProps) {
  return (
    <Link to={to} className="sanaa-course-card-link">
      <Card hoverable bordered className="sanaa-course-card" bodyStyle={{ padding: 16 }}>
        <CourseCover title={course.title} />
        <div className="sanaa-course-card__head">
          <h3 className="sanaa-course-card__title">{course.title}</h3>
          {showStatus ? <CourseStatusTag status={course.status} /> : null}
        </div>
        <p className="sanaa-course-card__price">{formatPrice(course.price, course.currency)}</p>
      </Card>
    </Link>
  );
}
