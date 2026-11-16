import { Avatar, Button, Card, Result, Skeleton, Tag } from '@arco-design/web-react';
import { IconLink } from '@arco-design/web-react/icon';
import { Link, useParams } from 'react-router-dom';

import { publicImageUrl } from '../api/profile';
import { CreatorCourses } from '../components/CreatorCourses';
import { usePublicInstructor } from '../hooks/publishers';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { fr } from '../i18n/fr';
import { safeHref } from '../lib/url';

const LINKS = [
  { key: 'websiteUrl', label: fr.me.websiteLabel },
  { key: 'instagramUrl', label: fr.me.instagramLabel },
  { key: 'linkedinUrl', label: fr.me.linkedinLabel },
  { key: 'youtubeUrl', label: fr.me.youtubeLabel },
] as const;

export function PublicInstructorPage() {
  const { id } = useParams<{ id: string }>();
  const parsed = Number(id);
  const userId = Number.isInteger(parsed) && parsed > 0 ? parsed : null;
  const query = usePublicInstructor(userId);
  useDocumentTitle(query.data?.fullName);

  if (userId === null) {
    return (
      <div className="sanaa-center-page">
        <Result
          status="404"
          title={fr.publicInstructor.notFoundTitle}
          subTitle={fr.publicInstructor.notFoundBody}
        />
      </div>
    );
  }

  if (query.isPending) {
    return (
      <div className="sanaa-page sanaa-page--narrow">
        <Card bordered className="sanaa-card" bodyStyle={{ padding: 32 }}>
          <Skeleton animation text={{ rows: 6 }} />
        </Card>
      </div>
    );
  }

  if (query.isError || !query.data) {
    return (
      <div className="sanaa-center-page">
        <Result
          status="404"
          title={fr.publicInstructor.notFoundTitle}
          subTitle={fr.publicInstructor.notFoundBody}
          extra={
            <Link to="/">
              <Button type="primary">{fr.common.backHome}</Button>
            </Link>
          }
        />
      </div>
    );
  }

  const instructor = query.data;
  const avatar = publicImageUrl(instructor.avatarImageId);
  // Hide the links block entirely when the instructor published no safe link.
  const links = LINKS.flatMap((link) => {
    const href = safeHref(instructor[link.key]);
    return href ? [{ key: link.key, label: link.label, href }] : [];
  });

  return (
    <main className="sanaa-page sanaa-page--narrow">
      <header className="sanaa-public__header">
        {avatar ? (
          <img className="sanaa-public__logo" src={avatar} alt="" />
        ) : (
          <Avatar size={88} className="sanaa-public__avatar">
            {instructor.fullName.charAt(0).toUpperCase()}
          </Avatar>
        )}
        <div className="sanaa-public__identity">
          <Tag>{fr.publicInstructor.publisherLabel}</Tag>
          <h1 className="sanaa-display sanaa-display--compact">{instructor.fullName}</h1>
          {instructor.headline ? <p className="sanaa-lead">{instructor.headline}</p> : null}
          <Link
            to={`/publishers/${encodeURIComponent(instructor.publisherSlug)}`}
            className="sanaa-link"
          >
            {instructor.publisherName}
          </Link>
        </div>
      </header>

      {instructor.bio ? (
        <Card bordered className="sanaa-card" bodyStyle={{ padding: 28 }}>
          <h2 className="sanaa-title-lg">{fr.publicInstructor.bioTitle}</h2>
          {/* Rendered as text: React escapes HTML, so a script in a bio stays visible text. */}
          <p className="sanaa-text sanaa-prose-text">{instructor.bio}</p>
        </Card>
      ) : null}

      <CreatorCourses
        title={fr.publicInstructor.coursesTitle}
        lead={fr.publicInstructor.coursesLead}
        empty={fr.publicInstructor.emptyCourses}
        matches={(course) =>
          course.instructorName === instructor.fullName &&
          course.publisherSlug === instructor.publisherSlug
        }
      />

      {links.length > 0 ? (
        <section className="sanaa-public__links" aria-label={fr.publicInstructor.linksTitle}>
          <h2 className="sanaa-title-md">{fr.publicInstructor.linksTitle}</h2>
          <ul className="sanaa-link-list sanaa-link-list--inline">
            {links.map((link) => (
              <li key={link.key}>
                <a className="sanaa-link" href={link.href} target="_blank" rel="noreferrer noopener">
                  <IconLink aria-hidden="true" /> {link.label}
                </a>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </main>
  );
}
