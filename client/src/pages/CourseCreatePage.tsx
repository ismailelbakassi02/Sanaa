import { Alert, Button, Card, Form, Message, Typography } from '@arco-design/web-react';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { CourseFields, type CourseFormValues } from '../components/CourseFields';
import { useCreateCourse } from '../hooks/courses';
import { fr } from '../i18n/fr';
import { courseErrorMessage } from '../lib/courseErrors';
import { useTenantSearch } from '../tenant/navigation';

export function CourseCreatePage() {
  const [form] = Form.useForm<CourseFormValues>();
  const mutation = useCreateCourse();
  const navigate = useNavigate();
  const tenantSearch = useTenantSearch();
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = (values: CourseFormValues) => {
    setError(null);
    mutation.mutate(
      {
        title: values.title.trim(),
        description: values.description?.trim() ? values.description.trim() : null,
        price: values.price,
        currency: 'MAD',
      },
      {
        onSuccess: (course) => {
          Message.success(fr.editor.created);
          // The curriculum is edited on the detail editor right after creation.
          navigate(`/manage/${course.id}/edit${tenantSearch}`);
        },
        onError: (mutationError) => {
          setError(courseErrorMessage(mutationError));
        },
      },
    );
  };

  return (
    <div className="sanaa-page sanaa-page--narrow">
      <header className="sanaa-page__head">
        <Typography.Title heading={3} className="sanaa-title-xl">
          {fr.editor.createTitle}
        </Typography.Title>
        <p className="sanaa-text sanaa-muted">{fr.manageCourses.subtitle}</p>
      </header>

      {error ? (
        <Alert
          type="error"
          showIcon
          content={error}
          closable
          onClose={() => setError(null)}
          className="sanaa-alert"
        />
      ) : null}

      <Card bordered bodyStyle={{ padding: 24 }}>
        <Form<CourseFormValues>
          form={form}
          layout="vertical"
          onSubmit={handleSubmit}
          disabled={mutation.isPending}
          scrollToFirstError
        >
          <CourseFields />
          <div className="sanaa-form-actions">
            <Button type="primary" htmlType="submit" loading={mutation.isPending}>
              {mutation.isPending ? fr.editor.creating : fr.editor.create}
            </Button>
            <Link to={`/manage${tenantSearch}`}>
              <Button>{fr.editor.cancel}</Button>
            </Link>
          </div>
        </Form>
      </Card>
    </div>
  );
}
