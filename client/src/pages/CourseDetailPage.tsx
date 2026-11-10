import {
  Button,
  Card,
  Collapse,
  Grid,
  Result,
  Skeleton,
  Tag,
  Typography,
} from '@arco-design/web-react';
import { IconLeft } from '@arco-design/web-react/icon';
import { useNavigate, useParams } from 'react-router-dom';

import { ApiError } from '../api/errors';
import { CourseCover } from '../components/CourseCover';
import { CourseStatusTag } from '../components/CourseStatusTag';
import { useCourse } from '../hooks/courses';
import { useMyEnrollments } from '../hooks/enrollments';
import { formatDuration, formatPrice } from '../i18n/format';
import { fr } from '../i18n/fr';
import { useAuthStore } from '../store/auth';
import { useTenantSearch } from '../tenant/navigation';

export function CourseDetailPage() {
  const { courseId } = useParams<{ courseId: string }>();
  const navigate = useNavigate();
  const tenantSearch = useTenantSearch();
  const user = useAuthStore((state) => state.user);
  const parsedId = Number(courseId);
  const id = Number.isInteger(parsedId) && parsedId > 0 ? parsedId : null;

  const query = useCourse(id);
  const isStudent = user?.role === 'STUDENT';
  const enrollments = useMyEnrollments(isStudent);

  const backToCatalogue = () => navigate(`/courses${tenantSearch}`);
  const enrolled =
    isStudent && enrollments.data?.some((enrollment) => enrollment.course.id === id) === true;
  const notFound = query.error instanceof ApiError && query.error.status === 404;

  return (
    <div className="sanaa-page">
      <Button type="text" icon={<IconLeft />} onClick={backToCatalogue} className="sanaa-back">
        {fr.courseDetail.back}
      </Button>

      {query.isPending ? (
        <Card bordered bodyStyle={{ padding: 24 }}>
          <Skeleton animation text={{ rows: 6 }} />
        </Card>
      ) : query.isError ? (
        <Result
          status={notFound ? '404' : 'warning'}
          title={notFound ? fr.courseDetail.notFoundTitle : fr.courseDetail.loadErrorTitle}
          subTitle={notFound ? fr.courseDetail.notFoundBody : fr.courseDetail.loadErrorBody}
          extra={
            <Button type="primary" onClick={notFound ? backToCatalogue : () => void query.refetch()}>
              {notFound ? fr.courseDetail.back : fr.common.retry}
            </Button>
          }
        />
      ) : (
        <Grid.Row gutter={[24, 24]}>
          <Grid.Col xs={24} lg={16}>
            <CourseCover title={query.data.title} size="detail" />
            <div className="sanaa-detail__head">
              <Typography.Title heading={2} className="sanaa-title-xl">
                {query.data.title}
              </Typography.Title>
              {!isStudent ? <CourseStatusTag status={query.data.status} /> : null}
            </div>
            {query.data.description ? (
              <p className="sanaa-text sanaa-detail__description">{query.data.description}</p>
            ) : null}

            <section className="sanaa-detail__section">
              <h2 className="sanaa-title-md sanaa-detail__section-title">
                {fr.courseDetail.curriculum}
              </h2>
              {query.data.modules.length === 0 ? (
                <p className="sanaa-text sanaa-muted">{fr.courseDetail.curriculumEmpty}</p>
              ) : (
                <Collapse
                  defaultActiveKey={query.data.modules.map((module) => String(module.id))}
                  bordered
                >
                  {query.data.modules.map((module) => (
                    <Collapse.Item
                      key={module.id}
                      name={String(module.id)}
                      header={
                        <span className="sanaa-module-head">
                          <span>{`${module.position}. ${module.title}`}</span>
                          <span className="sanaa-caption">
                            {fr.courseDetail.lessonCount(module.lessons.length)}
                          </span>
                        </span>
                      }
                    >
                      {module.lessons.length === 0 ? (
                        <p className="sanaa-caption">{fr.courseDetail.lessonEmpty}</p>
                      ) : (
                        <ul className="sanaa-lesson-list">
                          {module.lessons.map((lesson) => (
                            <li key={lesson.id} className="sanaa-lesson">
                              <span className="sanaa-lesson__title">{lesson.title}</span>
                              <span className="sanaa-lesson__meta">
                                {lesson.freePreview ? (
                                  <Tag color="arcoblue">{fr.courseDetail.freePreview}</Tag>
                                ) : null}
                                <span className="sanaa-caption">
                                  {formatDuration(lesson.durationSeconds)}
                                </span>
                              </span>
                            </li>
                          ))}
                        </ul>
                      )}
                    </Collapse.Item>
                  ))}
                </Collapse>
              )}
            </section>
          </Grid.Col>

          <Grid.Col xs={24} lg={8}>
            <Card bordered className="sanaa-detail-aside" bodyStyle={{ padding: 24 }}>
              <p className="sanaa-caption">{fr.courseDetail.priceLabel}</p>
              <p className="sanaa-price">{formatPrice(query.data.price, query.data.currency)}</p>
              {isStudent ? (
                enrolled ? (
                  <Tag color="green" className="sanaa-detail-aside__action">
                    {fr.courseDetail.enrolled}
                  </Tag>
                ) : (
                  <div className="sanaa-detail-aside__action">
                    <Button type="primary" long disabled>
                      {fr.courseDetail.buy}
                    </Button>
                    <p className="sanaa-caption sanaa-detail-aside__hint">
                      {fr.courseDetail.paymentSoon}
                    </p>
                  </div>
                )
              ) : null}
            </Card>
          </Grid.Col>
        </Grid.Row>
      )}
    </div>
  );
}
