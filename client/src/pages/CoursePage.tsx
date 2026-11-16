import {
  Button,
  Card,
  Collapse,
  Grid,
  Message,
  Result,
  Skeleton,
  Tag,
} from '@arco-design/web-react';
import { IconLeft, IconLock, IconUser } from '@arco-design/web-react/icon';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';

import { ApiError } from '../api/errors';
import { messageForError } from '../api/messages';
import { CourseCover } from '../components/CourseCover';
import { CourseReviews } from '../components/CourseReviews';
import { PriceBlock } from '../components/CourseCard';
import { ReviewForm } from '../components/ReviewForm';
import { usePublicCourse } from '../hooks/catalogue';
import { useEnrollFree, usePurchaseInfo } from '../hooks/learner';
import { useMyOpenPayment } from '../hooks/payments';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useMediaQuery } from '../hooks/useMediaQuery';
import { formatDate, formatDuration } from '../i18n/format';
import { fr } from '../i18n/fr';
import { courseAction, type CourseActionKind } from '../lib/courseAction';
import { useAuthStore } from '../store/auth';

function detailError(error: unknown): string {
  const api = error instanceof ApiError ? error : null;
  if (api?.status === 409) {
    return fr.coursePage.conflict;
  }
  if (api?.status === 403) {
    return fr.coursePage.forbidden;
  }
  return messageForError(error);
}

function PriceAction({
  kind,
  courseId,
  onLogin,
  purchaseTitle,
  purchasePrice,
  purchaseCurrency,
}: {
  kind: CourseActionKind;
  courseId: number;
  onLogin: () => void;
  purchaseTitle: string;
  purchasePrice: number;
  purchaseCurrency: string;
}) {
  const enroll = useEnrollFree(courseId);

  switch (kind) {
    case 'login':
      return (
        <Button type="primary" long onClick={onLogin}>
          {fr.coursePage.priceCard.loginToEnroll}
        </Button>
      );
    case 'enroll':
      return (
        <Button
          type="primary"
          long
          loading={enroll.isPending}
          onClick={() => {
            enroll.mutate(undefined, {
              onSuccess: () => Message.success(fr.coursePage.priceCard.enrolled),
              onError: (error) => Message.error(detailError(error)),
            });
          }}
        >
          {enroll.isPending ? fr.coursePage.priceCard.enrolling : fr.coursePage.priceCard.enroll}
        </Button>
      );
    case 'buy':
      return (
        <>
          <Button type="primary" long disabled>
            {fr.coursePage.priceCard.buy}
          </Button>
          <p className="sanaa-caption sanaa-detail-aside__hint">
            {fr.coursePage.priceCard.buySoon}
          </p>
        </>
      );
    case 'enrolled':
      return (
        <div className="sanaa-detail-aside__action">
          <Tag color="green">{fr.coursePage.priceCard.enrolled}</Tag>
          <Link className="sanaa-link sanaa-detail-aside__hint" to="/learn/courses">
            {fr.coursePage.priceCard.openMyCourses}
          </Link>
        </div>
      );
    case 'pending':
      return (
        <div className="sanaa-detail-aside__action">
          <Tag color="gold">{fr.coursePage.priceCard.pending}</Tag>
          <p className="sanaa-caption sanaa-detail-aside__hint">
            {fr.coursePage.priceCard.pendingHint}
          </p>
          <p className="sanaa-caption">{purchaseTitle} — {purchasePrice} {purchaseCurrency}</p>
        </div>
      );
    case 'own':
      return <p className="sanaa-caption">{fr.coursePage.priceCard.ownPublisher}</p>;
    default:
      return (
        <p className="sanaa-caption">{fr.coursePage.priceCard.unavailable}</p>
      );
  }
}

export function CoursePage() {
  const { courseId } = useParams<{ courseId: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const parsed = Number(courseId);
  const id = Number.isInteger(parsed) && parsed > 0 ? parsed : null;
  const user = useAuthStore((state) => state.user);
  const roleCanLearn = user !== null && user.role !== 'SUPER_ADMIN';
  const query = usePublicCourse(id);
  const purchase = usePurchaseInfo(id, roleCanLearn);
  const openPayment = useMyOpenPayment(roleCanLearn ? id : null);
  const compactPrice = useMediaQuery('(max-width: 1199px)');

  useDocumentTitle(query.data?.title ?? fr.search.title);

  const notFound = query.error instanceof ApiError && query.error.status === 404;

  if (id === null) {
    return (
      <main className="sanaa-center-page">
        <Result
          status="404"
          title={<h1 className="sanaa-result-title">{fr.coursePage.notFoundTitle}</h1>}
          subTitle={fr.coursePage.notFoundBody}
          extra={
            <Link to="/courses">
              <Button type="primary">{fr.coursePage.back}</Button>
            </Link>
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

  if (query.isError || !query.data) {
    return (
      <main className="sanaa-center-page">
        <Result
          status={notFound ? '404' : 'warning'}
          title={
            <h1 className="sanaa-result-title">
              {notFound ? fr.coursePage.notFoundTitle : fr.coursePage.loadErrorTitle}
            </h1>
          }
          subTitle={notFound ? fr.coursePage.notFoundBody : fr.coursePage.loadErrorBody}
          extra={
            notFound ? (
              <Link to="/courses">
                <Button type="primary">{fr.coursePage.back}</Button>
              </Link>
            ) : (
              <Button type="primary" onClick={() => void query.refetch()}>
                {fr.common.retry}
              </Button>
            )
          }
        />
      </main>
    );
  }

  const course = query.data;
  const info = purchase.data;
  const action = courseAction({
    authenticated: user !== null,
    enrolled: info?.alreadyEnrolled ?? false,
    ownPublisher: info?.ownPublisher ?? false,
    free: info?.free ?? false,
    pendingPayment: Boolean(openPayment.data),
    // The primary action must never be guessed from the public card: until the
    // trusted purchase info arrives, no action is offered.
    purchaseUnavailable: roleCanLearn
      ? purchase.isPending || purchase.isError
      : user !== null,
  });

  const moduleCount = course.modules.length;
  const lessonCount = course.modules.reduce(
    (total, module) => total + module.lessons.length,
    0,
  );
  const priceCard = (
    <Card bordered className="sanaa-detail-aside" bodyStyle={{ padding: 24 }}>
      <PriceBlock course={course} size="detail" />
      {info?.discountActive && info.discountEndsAt ? (
        <p className="sanaa-caption">
          {fr.marketplace.promotion} · {formatDate(info.discountEndsAt)}
        </p>
      ) : null}

      <p className="sanaa-detail-aside__students">
        <IconUser aria-hidden="true" /> {fr.coursePage.priceCard.students(course.studentCount)}
      </p>
      <p className="sanaa-caption">
        {fr.coursePage.priceCard.levelLabel} : {fr.levels[course.level]}
      </p>
      <p className="sanaa-caption">
        {fr.coursePage.priceCard.language(course.language)}
      </p>

      <div className="sanaa-detail-aside__primary">
        <PriceAction
          kind={action}
          courseId={id}
          onLogin={() =>
            navigate('/login', {
              state: { from: `${location.pathname}${location.search}` },
            })
          }
          purchaseTitle={info?.title ?? course.title}
          purchasePrice={info?.effectivePrice ?? course.effectivePrice}
          purchaseCurrency={info?.currency ?? course.currency}
        />
      </div>

      <p className="sanaa-caption sanaa-detail-aside__hint">
        {fr.coursePage.priceCard.watchSoon}
      </p>
    </Card>
  );

  return (
    <main className="sanaa-page">
      <Button type="text" icon={<IconLeft />} className="sanaa-back" onClick={() => navigate(-1)}>
        {fr.coursePage.back}
      </Button>

      <Grid.Row gutter={[32, 32]}>
        <Grid.Col xs={24} lg={16}>
          <CourseCover title={course.title} size="detail" />
          <div className="sanaa-detail__head">
            <h1 className="sanaa-title-xl">{course.title}</h1>
            <Tag>{fr.levels[course.level]}</Tag>
          </div>

          <div className="sanaa-detail__meta">
            <Link to={`/publishers/${encodeURIComponent(course.publisherSlug)}`} className="sanaa-link">
              {course.publisherName}
            </Link>
            <Link to={`/instructors/${course.instructorId}`} className="sanaa-caption sanaa-link">
              {fr.marketplace.byInstructor(course.instructorName)}
            </Link>
            <Link
              to={`/categories/${encodeURIComponent(course.categorySlug)}`}
              className="sanaa-caption sanaa-link"
            >
              {course.categoryName}
            </Link>
          </div>

          {course.summary ? <p className="sanaa-lead">{course.summary}</p> : null}

          {compactPrice ? <div className="sanaa-detail-mobile-price">{priceCard}</div> : null}

          {course.description ? (
            <section className="sanaa-detail__section">
              <h2 className="sanaa-title-lg sanaa-detail__section-title">
                {fr.coursePage.descriptionTitle}
              </h2>
              {/* Rendered as text: a script in a description stays visible text. */}
              <p className="sanaa-text sanaa-prose-text">{course.description}</p>
            </section>
          ) : null}

          {course.skills.length > 0 ? (
            <section className="sanaa-detail__section">
              <h2 className="sanaa-title-lg sanaa-detail__section-title">
                {fr.coursePage.skillsTitle}
              </h2>
              <ul className="sanaa-points">
                {course.skills.map((skill) => (
                  <li key={skill}>{skill}</li>
                ))}
              </ul>
            </section>
          ) : null}

          {course.requirements.length > 0 ? (
            <section className="sanaa-detail__section">
              <h2 className="sanaa-title-lg sanaa-detail__section-title">
                {fr.coursePage.requirementsTitle}
              </h2>
              <ul className="sanaa-points">
                {course.requirements.map((requirement) => (
                  <li key={requirement}>{requirement}</li>
                ))}
              </ul>
            </section>
          ) : null}

          <section className="sanaa-detail__section">
            <h2 className="sanaa-title-lg sanaa-detail__section-title">
              {fr.coursePage.curriculumTitle}
            </h2>
            <p className="sanaa-caption">{fr.coursePage.curriculumMeta(moduleCount, lessonCount)}</p>
            {moduleCount === 0 ? (
              <p className="sanaa-text sanaa-muted">{fr.coursePage.curriculumEmpty}</p>
            ) : (
              <Collapse defaultActiveKey={course.modules.map((module) => String(module.id))} bordered>
                {course.modules.map((module) => (
                  <Collapse.Item
                    key={module.id}
                    name={String(module.id)}
                    header={
                      <span className="sanaa-module-head">
                        <span>{`${module.position}. ${module.title}`}</span>
                        <span className="sanaa-caption">
                          {fr.coursePage.lessonCount(module.lessons.length)}
                        </span>
                      </span>
                    }
                  >
                    {module.lessons.length === 0 ? (
                      <p className="sanaa-caption">{fr.coursePage.lessonEmpty}</p>
                    ) : (
                      <ul className="sanaa-lesson-list">
                        {module.lessons.map((lesson) => (
                          <li key={lesson.id} className="sanaa-lesson">
                            <span className="sanaa-lesson__title">{lesson.title}</span>
                            <span className="sanaa-lesson__meta">
                              {lesson.freePreview ? (
                                <Tag color="arcoblue">{fr.coursePage.freePreview}</Tag>
                              ) : null}
                              {!lesson.available ? (
                                <IconLock aria-label={fr.common.soon} />
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

          <CourseReviews courseId={id} />

          <ReviewForm courseId={id} canReview={info?.alreadyEnrolled === true} />
        </Grid.Col>

        {compactPrice ? null : <Grid.Col xs={24} lg={8}>{priceCard}</Grid.Col>}
      </Grid.Row>

    </main>
  );
}
