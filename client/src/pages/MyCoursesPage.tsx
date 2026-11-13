import { Button, Card, Empty, Grid, Result, Skeleton, Typography } from '@arco-design/web-react';
import { IconRefresh } from '@arco-design/web-react/icon';
import { Link } from 'react-router-dom';

import { CourseCover } from '../components/CourseCover';
import { useMyEnrollments } from '../hooks/enrollments';
import { formatDate, formatPrice } from '../i18n/format';
import { fr } from '../i18n/fr';
import { useTenantSearch } from '../tenant/navigation';

const SKELETON_COUNT = 6;

function MyCoursesSkeleton() {
  return (
    <div className="sanaa-course-grid" data-testid="my-courses-skeleton">
      {Array.from({ length: SKELETON_COUNT }, (_, index) => (
        <Card key={index} bordered className="sanaa-course-card" bodyStyle={{ padding: 16 }}>
          <Skeleton
            animation
            image={{ shape: 'square', size: 'large', style: { width: '100%', height: 120 } }}
            text={{ rows: 3 }}
          />
        </Card>
      ))}
    </div>
  );
}

export function MyCoursesPage() {
  const query = useMyEnrollments();
  const tenantSearch = useTenantSearch();

  return (
    <div className="sanaa-page">
      <header className="sanaa-page__head sanaa-page__head--row">
        <div>
          <Typography.Title heading={3} className="sanaa-title-xl">
            {fr.myCourses.title}
          </Typography.Title>
          <p className="sanaa-text sanaa-muted">{fr.myCourses.subtitle}</p>
        </div>
        <Button
          icon={<IconRefresh />}
          loading={query.isFetching}
          onClick={() => void query.refetch()}
        >
          {fr.myCourses.refresh}
        </Button>
      </header>

      {query.isPending ? (
        <MyCoursesSkeleton />
      ) : query.isError ? (
        <Result
          status="warning"
          title={fr.myCourses.loadErrorTitle}
          subTitle={fr.myCourses.loadErrorBody}
          extra={
            <Button type="primary" onClick={() => void query.refetch()}>
              {fr.common.retry}
            </Button>
          }
        />
      ) : query.data.length === 0 ? (
        <div className="sanaa-empty">
          <Empty description={fr.myCourses.empty} />
          <Link to={`/courses${tenantSearch}`} className="sanaa-empty__action">
            <Button type="primary">{fr.myCourses.browse}</Button>
          </Link>
        </div>
      ) : (
        <Grid.Row gutter={[24, 24]}>
          {query.data.map((enrollment) => (
            <Grid.Col key={enrollment.id} xs={24} sm={12} lg={8} xl={6}>
              <Link
                to={`/courses/${enrollment.course.id}${tenantSearch}`}
                className="sanaa-course-card-link"
              >
                <Card hoverable bordered className="sanaa-course-card" bodyStyle={{ padding: 16 }}>
                  <CourseCover title={enrollment.course.title} />
                  <h3 className="sanaa-course-card__title">{enrollment.course.title}</h3>
                  <p className="sanaa-course-card__price">
                    {formatPrice(enrollment.course.price, enrollment.course.currency)}
                  </p>
                  <p className="sanaa-caption">
                    {fr.myCourses.enrolledAt(formatDate(enrollment.enrolledAt))}
                  </p>
                </Card>
              </Link>
            </Grid.Col>
          ))}
        </Grid.Row>
      )}
    </div>
  );
}
