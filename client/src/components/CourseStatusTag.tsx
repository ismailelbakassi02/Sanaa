import { Tag } from '@arco-design/web-react';

import type { CourseStatus } from '../api/courses';
import { fr } from '../i18n/fr';

/**
 * Status is paired with text, never carried by color alone. Colors follow the
 * semantic palette: draft is a warning, published is a success, archived is
 * neutral.
 */
const TAG_COLORS: Record<CourseStatus, string> = {
  DRAFT: 'orange',
  PUBLISHED: 'green',
  ARCHIVED: 'gray',
};

export function CourseStatusTag({ status }: { status: CourseStatus }) {
  return <Tag color={TAG_COLORS[status]}>{fr.status[status]}</Tag>;
}
