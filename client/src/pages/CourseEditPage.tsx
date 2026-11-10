import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Message,
  Modal,
  Popconfirm,
  Result,
  Skeleton,
  Switch,
  Tag,
  Typography,
} from '@arco-design/web-react';
import { IconPlus } from '@arco-design/web-react/icon';
import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import type { ModuleSummary } from '../api/courses';
import { ApiError } from '../api/errors';
import { CourseFields, type CourseFormValues } from '../components/CourseFields';
import { CourseStatusTag } from '../components/CourseStatusTag';
import {
  useAddLesson,
  useAddModule,
  useArchiveCourse,
  useCourse,
  usePublishCourse,
  useUpdateCourse,
} from '../hooks/courses';
import { formatDuration } from '../i18n/format';
import { fr } from '../i18n/fr';
import { courseErrorMessage } from '../lib/courseErrors';
import { useTenantSearch } from '../tenant/navigation';

interface ModuleFormValues {
  title: string;
}

interface LessonFormValues {
  title: string;
  durationSeconds: number;
  freePreview: boolean;
}

const moduleRules = [
  { required: true, message: fr.editor.validation.moduleTitleRequired },
  { maxLength: 120, message: fr.editor.validation.moduleTitleMax },
];

const lessonRules = [
  { required: true, message: fr.editor.validation.lessonTitleRequired },
  { maxLength: 120, message: fr.editor.validation.lessonTitleMax },
];

export function CourseEditPage() {
  const { courseId } = useParams<{ courseId: string }>();
  const parsedId = Number(courseId);
  const id = Number.isInteger(parsedId) && parsedId > 0 ? parsedId : 0;
  const validId = id > 0;
  const tenantSearch = useTenantSearch();

  const query = useCourse(validId ? id : null);
  const update = useUpdateCourse(id);
  const publish = usePublishCourse(id);
  const archive = useArchiveCourse(id);
  const addModule = useAddModule(id);
  const addLesson = useAddLesson(id);

  const [form] = Form.useForm<CourseFormValues>();
  const [moduleForm] = Form.useForm<ModuleFormValues>();
  const [lessonForm] = Form.useForm<LessonFormValues>();
  const [error, setError] = useState<string | null>(null);
  const [moduleVisible, setModuleVisible] = useState(false);
  const [lessonTarget, setLessonTarget] = useState<ModuleSummary | null>(null);
  const loadedCourseId = useRef<number | null>(null);

  const course = query.data;
  const archived = course?.status === 'ARCHIVED';
  const notFound = query.error instanceof ApiError && query.error.status === 404;

  // Only fill the form on the first load of a given course. A background
  // refetch (after adding a module) must not overwrite unsaved field edits.
  useEffect(() => {
    if (course && loadedCourseId.current !== course.id) {
      loadedCourseId.current = course.id;
      form.setFieldsValue({
        title: course.title,
        description: course.description ?? '',
        price: course.price,
      });
    }
  }, [course, form]);

  const reload = () => {
    setError(null);
    loadedCourseId.current = null;
    void query.refetch();
  };

  const handleSave = (values: CourseFormValues) => {
    if (!course) {
      return;
    }
    setError(null);
    update.mutate(
      {
        title: values.title.trim(),
        description: values.description?.trim() ? values.description.trim() : null,
        price: values.price,
        currency: 'MAD',
        version: course.version,
      },
      {
        onSuccess: () => Message.success(fr.editor.saved),
        onError: (mutationError) => setError(courseErrorMessage(mutationError)),
      },
    );
  };

  const handlePublish = () => {
    setError(null);
    publish.mutate(undefined, {
      onSuccess: () => Message.success(fr.editor.published),
      onError: (mutationError) => setError(courseErrorMessage(mutationError, 'publish')),
    });
  };

  const handleArchive = () => {
    setError(null);
    archive.mutate(undefined, {
      onSuccess: () => Message.success(fr.editor.archived),
      onError: (mutationError) => setError(courseErrorMessage(mutationError)),
    });
  };

  const openModuleModal = () => {
    moduleForm.resetFields();
    setModuleVisible(true);
  };

  const handleAddModule = async () => {
    let values: ModuleFormValues;
    try {
      values = await moduleForm.validate();
    } catch {
      return;
    }
    addModule.mutate(
      { title: values.title.trim() },
      {
        onSuccess: () => {
          Message.success(fr.editor.moduleAdded);
          setModuleVisible(false);
          moduleForm.resetFields();
        },
        onError: (mutationError) => {
          setModuleVisible(false);
          setError(courseErrorMessage(mutationError));
        },
      },
    );
  };

  const openLessonModal = (module: ModuleSummary) => {
    lessonForm.setFieldsValue({ title: '', durationSeconds: 300, freePreview: false });
    setLessonTarget(module);
  };

  const handleAddLesson = async () => {
    if (!lessonTarget) {
      return;
    }
    let values: LessonFormValues;
    try {
      values = await lessonForm.validate();
    } catch {
      return;
    }
    addLesson.mutate(
      {
        moduleId: lessonTarget.id,
        input: {
          title: values.title.trim(),
          durationSeconds: values.durationSeconds,
          freePreview: values.freePreview === true,
        },
      },
      {
        onSuccess: () => {
          Message.success(fr.editor.lessonAdded);
          setLessonTarget(null);
          lessonForm.resetFields();
        },
        onError: (mutationError) => {
          setLessonTarget(null);
          setError(courseErrorMessage(mutationError));
        },
      },
    );
  };

  if (!validId || notFound) {
    return (
      <div className="sanaa-page">
        <Result
          status="404"
          title={fr.editor.notFoundTitle}
          subTitle={fr.editor.notFoundBody}
          extra={
            <Link to={`/manage${tenantSearch}`}>
              <Button type="primary">{fr.editor.cancel}</Button>
            </Link>
          }
        />
      </div>
    );
  }

  if (query.isPending) {
    return (
      <div className="sanaa-page">
        <Card bordered bodyStyle={{ padding: 24 }}>
          <Skeleton animation text={{ rows: 8 }} />
        </Card>
      </div>
    );
  }

  if (query.isError || !course) {
    return (
      <div className="sanaa-page">
        <Result
          status="warning"
          title={fr.editor.loadErrorTitle}
          subTitle={fr.editor.loadErrorBody}
          extra={
            <Button type="primary" onClick={() => void query.refetch()}>
              {fr.common.retry}
            </Button>
          }
        />
      </div>
    );
  }

  return (
    <div className="sanaa-page sanaa-page--narrow">
      <header className="sanaa-page__head sanaa-page__head--row">
        <div>
          <Typography.Title heading={3} className="sanaa-title-xl">
            {fr.editor.editTitle}
          </Typography.Title>
          <p className="sanaa-text sanaa-muted">{course.title}</p>
        </div>
        <div className="sanaa-editor-status">
          <CourseStatusTag status={course.status} />
          <span className="sanaa-caption">
            {`${fr.editor.versionLabel} ${course.version}`}
          </span>
        </div>
      </header>

      {archived ? (
        <Alert
          type="info"
          showIcon
          content={fr.editor.archivedNotice}
          className="sanaa-alert"
        />
      ) : null}

      {error ? (
        <Alert
          type="error"
          showIcon
          content={
            <div className="sanaa-alert__content">
              <span>{error}</span>
              <Button size="mini" onClick={reload} className="sanaa-alert__action">
                {fr.common.retry}
              </Button>
            </div>
          }
          closable
          onClose={() => setError(null)}
          className="sanaa-alert"
        />
      ) : null}

      <Card bordered bodyStyle={{ padding: 24 }} className="sanaa-editor-card">
        <Form<CourseFormValues>
          form={form}
          layout="vertical"
          onSubmit={handleSave}
          disabled={archived || update.isPending}
          scrollToFirstError
        >
          <CourseFields disabled={archived} />
          <div className="sanaa-form-actions">
            <Button
              type="primary"
              htmlType="submit"
              loading={update.isPending}
              disabled={archived}
            >
              {update.isPending ? fr.editor.saving : fr.editor.save}
            </Button>
            <Link to={`/manage${tenantSearch}`}>
              <Button>{fr.editor.cancel}</Button>
            </Link>
            {!archived && course.status === 'DRAFT' ? (
              <Popconfirm
                title={fr.editor.publishConfirm}
                okText={fr.editor.confirm}
                cancelText={fr.editor.cancel}
                onOk={handlePublish}
              >
                <Button type="outline">{fr.editor.publish}</Button>
              </Popconfirm>
            ) : null}
            {!archived ? (
              <Popconfirm
                title={fr.editor.archiveConfirm}
                okText={fr.editor.confirm}
                cancelText={fr.editor.cancel}
                onOk={handleArchive}
              >
                <Button status="danger">{fr.editor.archive}</Button>
              </Popconfirm>
            ) : null}
          </div>
        </Form>
      </Card>

      <Card
        bordered
        bodyStyle={{ padding: 24 }}
        className="sanaa-editor-card"
        title={fr.editor.curriculumTitle}
        extra={
          archived ? null : (
            <Button type="text" icon={<IconPlus />} onClick={openModuleModal}>
              {fr.editor.addModule}
            </Button>
          )
        }
      >
        {course.modules.length === 0 ? (
          <p className="sanaa-text sanaa-muted">{fr.editor.curriculumEmpty}</p>
        ) : (
          <div className="sanaa-editor-modules">
            {course.modules.map((module) => (
              <section key={module.id} className="sanaa-editor-module">
                <header className="sanaa-editor-module__head">
                  <h3 className="sanaa-title-sm">{`${module.position}. ${module.title}`}</h3>
                  {archived ? null : (
                    <Button size="mini" icon={<IconPlus />} onClick={() => openLessonModal(module)}>
                      {fr.editor.addLesson}
                    </Button>
                  )}
                </header>
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
              </section>
            ))}
          </div>
        )}
      </Card>

      <Modal
        title={fr.editor.moduleModalTitle}
        visible={moduleVisible}
        onOk={() => void handleAddModule()}
        onCancel={() => setModuleVisible(false)}
        okText={fr.editor.addModule}
        cancelText={fr.editor.cancel}
        confirmLoading={addModule.isPending}
        unmountOnExit
      >
        <Form<ModuleFormValues> form={moduleForm} layout="vertical">
          <Form.Item label={fr.editor.moduleTitleLabel} field="title" rules={moduleRules}>
            <Input maxLength={120} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={fr.editor.lessonModalTitle}
        visible={lessonTarget !== null}
        onOk={() => void handleAddLesson()}
        onCancel={() => setLessonTarget(null)}
        okText={fr.editor.addLesson}
        cancelText={fr.editor.cancel}
        confirmLoading={addLesson.isPending}
        unmountOnExit
      >
        <Form<LessonFormValues> form={lessonForm} layout="vertical">
          <Form.Item label={fr.editor.lessonTitleLabel} field="title" rules={lessonRules}>
            <Input maxLength={120} />
          </Form.Item>
          <Form.Item
            label={fr.editor.durationLabel}
            field="durationSeconds"
            rules={[
              { required: true, message: fr.editor.validation.durationRequired },
              { min: 0, message: fr.editor.validation.durationMin },
            ]}
          >
            <InputNumber min={0} step={30} precision={0} />
          </Form.Item>
          <Form.Item
            label={fr.editor.freePreviewLabel}
            field="freePreview"
            triggerPropName="checked"
          >
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
