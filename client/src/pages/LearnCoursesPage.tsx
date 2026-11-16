import { Button, Card, Empty, Pagination, Result, Skeleton, Tag } from '@arco-design/web-react';
import { IconLeft } from '@arco-design/web-react/icon';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { CourseCover } from '../components/CourseCover';
import { useMyEnrollments } from '../hooks/learner';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { formatDateTimeShort } from '../i18n/format';
import { fr } from '../i18n/fr';

const PAGE_SIZE = 8;

const SOURCE_COLOR = {
  FREE: 'green',
  PAYMENT: 'arcoblue',
  ADMIN_GRANT: 'gold',
} as const;

export function LearnCoursesPage() {
  useDocumentTitle(fr.learner.courses.title);
  const navigate = useNavigate();
  const query = useMyEnrollments(true);
  const [page, setPage] = useState(1);

  if (query.isError) {
    return (
      <main className="sanaa-center-page">
        <Result
          status="warning"
          title={<h1 className="sanaa-result-title">{fr.learner.courses.loadErrorTitle}</h1>}
          subTitle={fr.learner.courses.loadErrorBody}
          extra={
            <Button type="primary" onClick={() => void query.refetch()}>
              {fr.common.retry}
            </Button>
          }
        />
      </main>
    );
  }

  if (query.isPending) {
    return (
      <main className="sanaa-page">
        <Card bordered bodyStyle={{ padding: 24 }}>
          <Skeleton animation text={{ rows: 6 }} />
        </Card>
      </main>
    );
  }

  const enrollments = query.data;
  const totalPages = Math.max(1, Math.ceil(enrollments.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages);
  const visible = enrollments.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE);

  return (
    <main className="sanaa-page">
      <Button
        type="text"
        icon={<IconLeft />}
        className="sanaa-back"
        onClick={() => navigate('/learn')}
      >
        {fr.learner.courses.backToHome}
      </Button>

      <header className="sanaa-page__head">
        <h1 className="sanaa-title-xl">{fr.learner.courses.title}</h1>
        <p className="sanaa-text sanaa-muted">{fr.learner.courses.subtitle}</p>
      </header>

      {enrollments.length === 0 ? (
        <Card bordered className="sanaa-card sanaa-empty-card" bodyStyle={{ padding: 32 }}>
          <Empty
            description={
              <span>
                <strong className="sanaa-empty__title">{fr.learner.courses.emptyTitle}</strong>
                <span className="sanaa-caption">{fr.learner.courses.emptyBody}</span>
              </span>
            }
          />
          <Link to="/courses">
            <Button type="primary" size="large">
              {fr.learner.courses.emptyAction}
            </Button>
          </Link>
        </Card>
      ) : (
        <>
          <ul className="sanaa-enrollment-list">
            {visible.map((enrollment) => (
              <li key={enrollment.enrollmentId}>
                <Card bordered className="sanaa-enrollment" bodyStyle={{ padding: 16 }}>
                  <CourseCover title={enrollment.course.title} />
                  <div className="sanaa-enrollment__body">
                    <Link
                      to={`/courses/${enrollment.course.id}`}
                      className="sanaa-title-md sanaa-link"
                    >
                      {enrollment.course.title}
                    </Link>
                    <span className="sanaa-caption">{enrollment.course.publisherName}</span>
                    <span className="sanaa-caption">
                      {fr.learner.courses.enrolledAt(formatDateTimeShort(enrollment.enrolledAt))}
                    </span>
                  </div>
                  <div className="sanaa-enrollment__actions">
                    <Tag color={SOURCE_COLOR[enrollment.source]}>
                      {fr.enrollmentSources[enrollment.source]}
                    </Tag>
                    <Button disabled>{fr.learner.courses.watch}</Button>
                    <span className="sanaa-caption">{fr.learner.courses.watchSoon}</span>
                  </div>
                </Card>
              </li>
            ))}
          </ul>

          {totalPages > 1 ? (
            <div className="sanaa-pagination">
              <Pagination
                current={safePage}
                pageSize={PAGE_SIZE}
                total={enrollments.length}
                onChange={(next) => {
                  setPage(next);
                  window.scrollTo({ top: 0, behavior: 'smooth' });
                }}
                showTotal
              />
            </div>
          ) : null}
        </>
      )}
    </main>
  );
}
