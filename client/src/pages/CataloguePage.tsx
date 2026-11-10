import {
  Button,
  Card,
  Empty,
  Pagination,
  Result,
  Skeleton,
  Typography,
} from '@arco-design/web-react';
import { useState } from 'react';

import { CourseCard } from '../components/CourseCard';
import { CATALOGUE_PAGE_SIZE, useCatalogue } from '../hooks/courses';
import { fr } from '../i18n/fr';
import { useAuthStore } from '../store/auth';
import { useTenantSearch } from '../tenant/navigation';

const SKELETON_COUNT = 8;

function CatalogueSkeleton() {
  return (
    <div className="sanaa-course-grid" data-testid="catalogue-skeleton">
      {Array.from({ length: SKELETON_COUNT }, (_, index) => (
        <Card key={index} bordered className="sanaa-course-card" bodyStyle={{ padding: 16 }}>
          <Skeleton
            animation
            image={{ shape: 'square', size: 'large', style: { width: '100%', height: 120 } }}
            text={{ rows: 2 }}
          />
        </Card>
      ))}
    </div>
  );
}

export function CataloguePage() {
  const user = useAuthStore((state) => state.user);
  const tenantSearch = useTenantSearch();
  const [page, setPage] = useState(1);
  const query = useCatalogue(page);
  // Students only see published courses, so a status tag would be noise for them.
  const showStatus = user?.role !== 'STUDENT';

  return (
    <div className="sanaa-page">
      <header className="sanaa-page__head">
        <Typography.Title heading={3} className="sanaa-title-xl">
          {fr.catalogue.title}
        </Typography.Title>
        <p className="sanaa-text sanaa-muted">{fr.catalogue.subtitle}</p>
      </header>

      {query.isPending ? (
        <CatalogueSkeleton />
      ) : query.isError ? (
        <Result
          status="warning"
          title={fr.catalogue.loadErrorTitle}
          subTitle={fr.catalogue.loadErrorBody}
          extra={
            <Button type="primary" onClick={() => void query.refetch()}>
              {fr.common.retry}
            </Button>
          }
        />
      ) : query.data.items.length === 0 ? (
        <Empty description={fr.catalogue.empty} />
      ) : (
        <>
          <div className="sanaa-course-grid">
            {query.data.items.map((course) => (
              <CourseCard
                key={course.id}
                course={course}
                showStatus={showStatus}
                to={`/courses/${course.id}${tenantSearch}`}
              />
            ))}
          </div>
          {/* A single page of results needs no pager: "Total 2 / 1" is noise. */}
          {query.data.totalPages > 1 ? (
            <div className="sanaa-pagination" data-testid="catalogue-pagination">
              <Pagination
                current={page}
                pageSize={CATALOGUE_PAGE_SIZE}
                total={query.data.totalElements}
                onChange={(nextPage) => setPage(nextPage)}
                showTotal
              />
            </div>
          ) : null}
        </>
      )}
    </div>
  );
}
