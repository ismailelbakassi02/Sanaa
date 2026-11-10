import {
  Button,
  Empty,
  Result,
  Skeleton,
  Table,
  Typography,
  type TableColumnProps,
} from '@arco-design/web-react';
import { IconEdit, IconPlus } from '@arco-design/web-react/icon';
import { useState } from 'react';
import { Link } from 'react-router-dom';

import type { CourseSummary } from '../api/courses';
import { CourseStatusTag } from '../components/CourseStatusTag';
import { useLessonCounts, useManagedCourses } from '../hooks/courses';
import { formatPrice } from '../i18n/format';
import { fr } from '../i18n/fr';
import { useAuthStore } from '../store/auth';
import { useTenantSearch } from '../tenant/navigation';

const PAGE_SIZE = 10;

export function ManageCoursesPage() {
  const user = useAuthStore((state) => state.user);
  const tenantSearch = useTenantSearch();
  const query = useManagedCourses();
  const [page, setPage] = useState(1);

  const courses = query.data ?? [];
  const visible = courses.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  const lessonCounts = useLessonCounts(visible.map((course) => course.id));

  const columns: TableColumnProps<CourseSummary>[] = [
    {
      title: fr.manageCourses.columnTitle,
      dataIndex: 'title',
      render: (_, course) => (
        <Link to={`/manage/${course.id}/edit${tenantSearch}`} className="sanaa-link">
          {course.title}
        </Link>
      ),
    },
    {
      title: fr.manageCourses.columnStatus,
      dataIndex: 'status',
      width: 130,
      render: (_, course) => <CourseStatusTag status={course.status} />,
    },
    {
      title: fr.manageCourses.columnPrice,
      dataIndex: 'price',
      width: 140,
      align: 'right',
      render: (_, course) => formatPrice(course.price, course.currency),
    },
    {
      title: fr.manageCourses.columnLessons,
      width: 110,
      align: 'right',
      render: (_, course) => lessonCounts.get(course.id) ?? '—',
    },
    {
      title: fr.manageCourses.columnActions,
      width: 130,
      align: 'right',
      render: (_, course) => (
        <Link to={`/manage/${course.id}/edit${tenantSearch}`}>
          <Button type="text" size="small" icon={<IconEdit />}>
            {fr.manageCourses.edit}
          </Button>
        </Link>
      ),
    },
  ];

  return (
    <div className="sanaa-page">
      <header className="sanaa-page__head sanaa-page__head--row">
        <div>
          <Typography.Title heading={3} className="sanaa-title-xl">
            {fr.manageCourses.title}
          </Typography.Title>
          <p className="sanaa-text sanaa-muted">
            {user?.role === 'TENANT_ADMIN'
              ? fr.manageCourses.adminSubtitle
              : fr.manageCourses.subtitle}
          </p>
        </div>
        <Link to={`/manage/new${tenantSearch}`}>
          <Button type="primary" icon={<IconPlus />}>
            {fr.manageCourses.create}
          </Button>
        </Link>
      </header>

      {query.isPending ? (
        <div className="sanaa-table-skeleton" data-testid="manage-skeleton">
          <Skeleton animation text={{ rows: 6 }} />
        </div>
      ) : query.isError ? (
        <Result
          status="warning"
          title={fr.manageCourses.loadErrorTitle}
          subTitle={fr.manageCourses.loadErrorBody}
          extra={
            <Button type="primary" onClick={() => void query.refetch()}>
              {fr.common.retry}
            </Button>
          }
        />
      ) : courses.length === 0 ? (
        <div className="sanaa-empty">
          <Empty description={fr.manageCourses.empty} />
          <Link to={`/manage/new${tenantSearch}`} className="sanaa-empty__action">
            <Button type="primary">{fr.manageCourses.create}</Button>
          </Link>
        </div>
      ) : (
        <Table<CourseSummary>
          rowKey="id"
          columns={columns}
          data={courses}
          pagination={{
            current: page,
            pageSize: PAGE_SIZE,
            total: courses.length,
            showTotal: true,
            onChange: (nextPage) => setPage(nextPage),
          }}
        />
      )}
    </div>
  );
}
