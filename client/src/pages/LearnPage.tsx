import { Button, Card, Empty, Result, Skeleton, Tag } from '@arco-design/web-react';
import { IconRight, IconUser } from '@arco-design/web-react/icon';
import { Link } from 'react-router-dom';

import { CourseCard } from '../components/CourseCard';
import { CourseCover } from '../components/CourseCover';
import { useCategories, useTrending } from '../hooks/catalogue';
import { useLearnerHome } from '../hooks/learner';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { formatDateTimeShort } from '../i18n/format';
import { fr } from '../i18n/fr';
import { useAuthStore } from '../store/auth';

const LATEST_COURSES = 6;
const SUGGESTED_TRENDING = 4;

function Loading() {
  return (
    <div className="sanaa-course-grid" aria-hidden="true">
      {Array.from({ length: 3 }, (_, index) => (
        <Card key={index} bordered className="sanaa-course-card">
          <Skeleton animation text={{ rows: 4 }} />
        </Card>
      ))}
    </div>
  );
}

export function LearnPage() {
  useDocumentTitle(fr.learner.home.title);
  const user = useAuthStore((state) => state.user);
  const query = useLearnerHome(true);
  const trending = useTrending(SUGGESTED_TRENDING);
  const categories = useCategories();

  if (query.isError) {
    return (
      <main className="sanaa-center-page">
        <Result
          status="warning"
          title={<h1 className="sanaa-result-title">{fr.learner.home.loadErrorTitle}</h1>}
          subTitle={fr.learner.home.loadErrorBody}
          extra={
            <Button type="primary" onClick={() => void query.refetch()}>
              {fr.common.retry}
            </Button>
          }
        />
      </main>
    );
  }

  const data = query.data;
  const latest = data?.myCourses.slice(0, LATEST_COURSES) ?? [];

  return (
    <main className="sanaa-page">
      <header className="sanaa-page__head">
        <p className="sanaa-caption">
          {user ? fr.learner.home.greeting(user.fullName) : fr.learner.home.title}
        </p>
        <h1 className="sanaa-title-xl">{fr.learner.home.title}</h1>
        <p className="sanaa-text sanaa-muted">{fr.learner.home.subtitle}</p>
      </header>

      {query.isPending ? (
        <Loading />
      ) : latest.length === 0 ? (
        <Card bordered className="sanaa-card sanaa-empty-card" bodyStyle={{ padding: 32 }}>
          <Empty
            description={
              <span>
                <strong className="sanaa-empty__title">{fr.learner.home.emptyTitle}</strong>
                <span className="sanaa-caption">{fr.learner.home.emptyBody}</span>
              </span>
            }
          />
          <Link to="/courses">
            <Button type="primary" size="large">
              {fr.learner.home.emptyAction}
            </Button>
          </Link>
        </Card>
      ) : (
        <>
          <section aria-labelledby="learn-resume-title">
            <div className="sanaa-section-head">
              <div>
                <h2 id="learn-resume-title" className="sanaa-title-lg">
                  {fr.learner.home.resumeTitle}
                </h2>
                <p className="sanaa-text sanaa-muted">{fr.learner.home.resumeLead}</p>
              </div>
              <Link to="/learn/courses" className="sanaa-link">
                {fr.learner.home.seeAllCourses}
              </Link>
            </div>
            <ul className="sanaa-resume__list">
              {latest.map((enrollment) => (
                <li key={enrollment.enrollmentId}>
                  <Link to={`/courses/${enrollment.course.id}`} className="sanaa-resume__item">
                    <CourseCover title={enrollment.course.title} />
                    <span className="sanaa-resume__body">
                      <span className="sanaa-resume__title">{enrollment.course.title}</span>
                      <span className="sanaa-caption">{enrollment.course.publisherName}</span>
                      <span className="sanaa-caption">
                        {fr.learner.courses.enrolledAt(formatDateTimeShort(enrollment.enrolledAt))}
                      </span>
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          </section>

          {data && data.myTeachers.length > 0 ? (
            <section aria-labelledby="learn-teachers-title">
              <div className="sanaa-section-head">
                <div>
                  <h2 id="learn-teachers-title" className="sanaa-title-lg">
                    {fr.learner.home.teachersTitle}
                  </h2>
                  <p className="sanaa-text sanaa-muted">{fr.learner.home.teachersLead}</p>
                </div>
              </div>
              <ul className="sanaa-link-list sanaa-link-list--inline">
                {data.myTeachers.map((teacher) => (
                  <li key={teacher.instructorId}>
                    <Link to={`/instructors/${teacher.instructorId}`} className="sanaa-link">
                      <IconUser aria-hidden="true" /> {teacher.instructorName}
                      <span className="sanaa-caption"> · {teacher.publisherName}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}
        </>
      )}

      {data && data.suggestedCategories.length > 0 ? (
        <section aria-labelledby="learn-suggested-title">
          <div className="sanaa-section-head">
            <h2 id="learn-suggested-title" className="sanaa-title-lg">
              {fr.learner.home.suggestedTitle}
            </h2>
          </div>
          <div className="sanaa-chips">
            {data.suggestedCategories.map((category) => (
              <Link
                key={category.id}
                to={`/courses?category=${encodeURIComponent(category.slug)}`}
                className="sanaa-link"
              >
                <Tag>{`${category.name} · ${fr.marketplace.categoryCourseCount(category.courseCount)}`}</Tag>
              </Link>
            ))}
          </div>
        </section>
      ) : null}

      <section aria-labelledby="learn-trending-title">
        <div className="sanaa-section-head">
          <div>
            <h2 id="learn-trending-title" className="sanaa-title-lg">
              {fr.learner.home.trendingTitle}
            </h2>
          </div>
          <Link to="/courses" className="sanaa-link">
            {fr.home.trendingAction} <IconRight aria-hidden="true" />
          </Link>
        </div>
        {trending.isPending ? (
          <Loading />
        ) : trending.data && trending.data.length > 0 ? (
          <div className="sanaa-course-grid">
            {trending.data.map((course) => (
              <CourseCard key={course.id} course={course} />
            ))}
          </div>
        ) : null}
      </section>

      {categories.data && categories.data.length > 0 ? (
        <section aria-labelledby="learn-categories-title">
          <div className="sanaa-section-head">
            <h2 id="learn-categories-title" className="sanaa-title-lg">
              {fr.learner.home.allCategoriesTitle}
            </h2>
          </div>
          <ul className="sanaa-category-grid">
            {categories.data.map((category) => (
              <li key={category.id}>
                <Link
                  to={`/categories/${encodeURIComponent(category.slug)}`}
                  className="sanaa-category-card"
                >
                  <span className="sanaa-category-card__name">{category.name}</span>
                  <span className="sanaa-category-card__count">
                    {fr.marketplace.categoryCourseCount(category.courseCount)}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </main>
  );
}
