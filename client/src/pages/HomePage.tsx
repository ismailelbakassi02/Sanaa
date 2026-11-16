import { Button, Card, Empty, Input, Skeleton, Tag } from '@arco-design/web-react';
import { IconArrowRight, IconSearch } from '@arco-design/web-react/icon';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import type { PublicCourseCard } from '../api/catalogue';
import { BrandLogo } from '../components/BrandLogo';
import { CourseCard } from '../components/CourseCard';
import { CourseCover } from '../components/CourseCover';
import { fr } from '../i18n/fr';
import { formatDateTimeShort } from '../i18n/format';
import { useCategories, useTrending } from '../hooks/catalogue';
import { useLearnerHome } from '../hooks/learner';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useAuthStore } from '../store/auth';

const TRENDING_LIMIT = 8;
const RESUME_LIMIT = 3;

function CategoryGrid() {
  const query = useCategories();
  if (query.isPending) {
    return (
      <div className="sanaa-category-grid" aria-hidden="true">
        {Array.from({ length: 6 }, (_, index) => (
          <Card key={index} bordered className="sanaa-category-card">
            <Skeleton animation text={{ rows: 2 }} />
          </Card>
        ))}
      </div>
    );
  }
  if (query.isError) {
    return <p className="sanaa-text sanaa-muted">{fr.search.loadErrorBody}</p>;
  }
  if (query.data.length === 0) {
    return <Empty description={fr.search.emptyBody} />;
  }
  return (
    <ul className="sanaa-category-grid">
      {query.data.map((category) => (
        <li key={category.id}>
          <Link
            to={`/categories/${encodeURIComponent(category.slug)}`}
            className="sanaa-category-card"
          >
            <span className="sanaa-category-card__name">{category.name}</span>
            <span className="sanaa-category-card__count">
              {fr.marketplace.categoryCourseCount(category.courseCount)}
            </span>
            <IconArrowRight aria-hidden="true" className="sanaa-category-card__arrow" />
          </Link>
        </li>
      ))}
    </ul>
  );
}

function TrendingGrid() {
  const query = useTrending(TRENDING_LIMIT);
  if (query.isPending) {
    return (
      <div className="sanaa-course-grid" aria-hidden="true">
        {Array.from({ length: 4 }, (_, index) => (
          <Card key={index} bordered className="sanaa-course-card">
            <Skeleton animation text={{ rows: 4 }} />
          </Card>
        ))}
      </div>
    );
  }
  if (query.isError) {
    return <p className="sanaa-text sanaa-muted">{fr.search.loadErrorBody}</p>;
  }
  return (
    <div className="sanaa-course-grid">
      {query.data.map((course: PublicCourseCard) => (
        <CourseCard key={course.id} course={course} />
      ))}
    </div>
  );
}

function ResumeStrip() {
  const user = useAuthStore((state) => state.user);
  const enabled = user !== null && user.role !== 'SUPER_ADMIN';
  const query = useLearnerHome(enabled);
  if (!enabled || query.isPending || query.isError) {
    return null;
  }
  const latest = query.data.myCourses.slice(0, RESUME_LIMIT);
  if (latest.length === 0) {
    return null;
  }
  return (
    <section className="sanaa-resume" aria-labelledby="home-resume-title">
      <div className="sanaa-section-head">
        <div>
          <h2 id="home-resume-title" className="sanaa-title-lg">
            {fr.home.resumeTitle}
          </h2>
          <p className="sanaa-text sanaa-muted">{fr.home.resumeLead}</p>
        </div>
        <Link to="/learn/courses" className="sanaa-link">
          {fr.home.resumeAction}
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
  );
}

export function HomePage() {
  useDocumentTitle(null);
  const user = useAuthStore((state) => state.user);
  const navigate = useNavigate();
  const [term, setTerm] = useState('');

  const submitSearch = () => {
    const query = term.trim();
    navigate(query ? `/courses?q=${encodeURIComponent(query)}` : '/courses');
  };

  return (
    <main className="sanaa-home">
      <section className="sanaa-hero">
        <div className="sanaa-hero__content">
          <Tag className="sanaa-hero__eyebrow">{fr.app.headerTagline}</Tag>
          <h1 className="sanaa-display">{fr.home.heroTitle}</h1>
          <p className="sanaa-lead">{fr.home.heroLead}</p>
          <form
            className="sanaa-hero-search"
            role="search"
            onSubmit={(event) => {
              event.preventDefault();
              submitSearch();
            }}
          >
            <Input
              size="large"
              value={term}
              onChange={(value) => setTerm(value)}
              placeholder={fr.home.heroSearchPlaceholder}
              aria-label={fr.home.heroSearchLabel}
              prefix={<IconSearch aria-hidden="true" />}
            />
            <Button type="primary" size="large" htmlType="submit">
              {fr.home.heroSearchAction}
            </Button>
          </form>
          <div className="sanaa-hero__actions">
            <Link to="/courses">
              <Button size="large">{fr.home.browseCourses}</Button>
            </Link>
            {user ? (
              <Link to={user.mustChangePassword ? '/password' : '/me'}>
                <Button size="large">{fr.home.openSpace}</Button>
              </Link>
            ) : (
              <Link to="/register">
                <Button size="large">{fr.home.startLearning}</Button>
              </Link>
            )}
          </div>
          <Link to="/privacy" className="sanaa-hero__privacy sanaa-link">
            {fr.home.privacyLink}
          </Link>
        </div>

        <div className="sanaa-hero__panel">
          <BrandLogo variant="full" height={88} />
          <p className="sanaa-hero__panel-text">{fr.app.panelCaption}</p>
          <ul className="sanaa-hero__points">
            {fr.home.panelPoints.map((point) => (
              <li key={point}>{point}</li>
            ))}
          </ul>
        </div>
      </section>

      <ResumeStrip />

      <section aria-labelledby="home-categories-title">
        <div className="sanaa-section-head">
          <div>
            <h2 id="home-categories-title" className="sanaa-title-lg">
              {fr.home.categoriesTitle}
            </h2>
            <p className="sanaa-text sanaa-muted">{fr.home.categoriesLead}</p>
          </div>
        </div>
        <CategoryGrid />
      </section>

      <section aria-labelledby="home-trending-title">
        <div className="sanaa-section-head">
          <div>
            <h2 id="home-trending-title" className="sanaa-title-lg">
              {fr.home.trendingTitle}
            </h2>
            <p className="sanaa-text sanaa-muted">{fr.home.trendingLead}</p>
          </div>
          <Link to="/courses" className="sanaa-link">
            {fr.home.trendingAction}
          </Link>
        </div>
        <TrendingGrid />
      </section>

      <section className="sanaa-callout">
        <div>
          <h2 className="sanaa-title-lg">{fr.home.creatorBandTitle}</h2>
          <p className="sanaa-text sanaa-muted">{fr.home.creatorBandLead}</p>
        </div>
        <Link to="/become-creator">
          <Button type="primary" size="large">
            {fr.home.calloutAction}
          </Button>
        </Link>
      </section>
    </main>
  );
}
