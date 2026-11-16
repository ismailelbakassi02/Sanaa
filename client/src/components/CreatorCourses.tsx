import { Card, Empty, Skeleton } from '@arco-design/web-react';

import type { PublicCourseCard } from '../api/catalogue';
import { useCatalogueWindow } from '../hooks/catalogue';
import { CourseCard } from './CourseCard';

interface CreatorCoursesProps {
  title: string;
  lead: string;
  empty: string;
  /** Local filter: the public search has no creator filter yet (ADR 005). */
  matches: (course: PublicCourseCard) => boolean;
}

/**
 * The published courses of one creator or instructor. It shares the catalogue
 * card so prices, ratings and covers stay identical everywhere.
 */
export function CreatorCourses({ title, lead, empty, matches }: CreatorCoursesProps) {
  const query = useCatalogueWindow();
  const courses = (query.data?.items ?? []).filter(matches);

  return (
    <section className="sanaa-creator-courses" aria-labelledby="creator-courses-title">
      <div className="sanaa-section-head">
        <div>
          <h2 id="creator-courses-title" className="sanaa-title-lg">
            {title}
          </h2>
          <p className="sanaa-text sanaa-muted">{lead}</p>
        </div>
      </div>

      {query.isPending ? (
        <div className="sanaa-course-grid" aria-hidden="true">
          {Array.from({ length: 3 }, (_, index) => (
            <Card key={index} bordered className="sanaa-course-card">
              <Skeleton animation text={{ rows: 4 }} />
            </Card>
          ))}
        </div>
      ) : courses.length === 0 ? (
        <Empty description={empty} />
      ) : (
        <div className="sanaa-course-grid">
          {courses.map((course) => (
            <CourseCard key={course.id} course={course} />
          ))}
        </div>
      )}
    </section>
  );
}
