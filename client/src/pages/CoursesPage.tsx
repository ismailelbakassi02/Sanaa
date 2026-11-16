import {
  Button,
  Card,
  Drawer,
  Empty,
  Input,
  Pagination,
  Select,
  Skeleton,
  Switch,
  Tag,
} from '@arco-design/web-react';
import { IconClose, IconFilter, IconLeft } from '@arco-design/web-react/icon';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

import { CATALOGUE_PAGE_SIZE, COURSE_SORTS, type CourseSearchParams } from '../api/catalogue';
import type { CourseLevel } from '../api/types';
import { CourseCard } from '../components/CourseCard';
import { useCategories, useCourseSearch } from '../hooks/catalogue';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useMediaQuery } from '../hooks/useMediaQuery';
import { fr } from '../i18n/fr';
import {
  hasActiveFilters,
  parseCatalogueSearch,
  serializeCatalogueSearch,
  type CatalogueFilters,
} from '../lib/searchParams';

const LEVELS: readonly CourseLevel[] = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'];
const SEARCH_DEBOUNCE_MS = 300;

interface FilterChip {
  key: string;
  label: string;
  patch: Partial<CatalogueFilters>;
}

interface FilterPanelProps {
  filters: CatalogueFilters;
  categories: { slug: string; name: string }[];
  onChange: (patch: Partial<CatalogueFilters>) => void;
  onReset: () => void;
  onApply?: () => void;
}

function FilterPanel({ filters, categories, onChange, onReset, onApply }: FilterPanelProps) {
  return (
    <div className="sanaa-filters">
      <div className="sanaa-field">
        <label className="sanaa-field__label" htmlFor="filter-category">
          {fr.search.categoryLabel}
        </label>
        <Select
          id="filter-category"
          value={filters.category}
          onChange={(value) => onChange({ category: value ?? '' })}
        >
          <Select.Option value="">{fr.search.allCategories}</Select.Option>
          {categories.map((category) => (
            <Select.Option key={category.slug} value={category.slug}>
              {category.name}
            </Select.Option>
          ))}
        </Select>
      </div>

      <div className="sanaa-field">
        <label className="sanaa-field__label" htmlFor="filter-level">
          {fr.search.levelLabel}
        </label>
        <Select
          id="filter-level"
          value={filters.level}
          onChange={(value) => onChange({ level: (value as CourseLevel) ?? '' })}
        >
          <Select.Option value="">{fr.search.allLevels}</Select.Option>
          {LEVELS.map((level) => (
            <Select.Option key={level} value={level}>
              {fr.levels[level]}
            </Select.Option>
          ))}
        </Select>
      </div>

      <div className="sanaa-field">
        <span className="sanaa-field__label">{fr.search.sortLabel}</span>
        <Select
          value={filters.sort}
          onChange={(value) =>
            onChange({ sort: value as CatalogueFilters['sort'] })
          }
        >
          {COURSE_SORTS.map((sort) => (
            <Select.Option key={sort} value={sort}>
              {fr.search.sorts[sort]}
            </Select.Option>
          ))}
        </Select>
      </div>

      <label className="sanaa-filter-switch">
        <Switch
          checked={filters.free}
          onChange={(checked) => onChange({ free: checked })}
        />
        <span>{fr.search.freeOnly}</span>
      </label>

      <div className="sanaa-form-actions">
        <Button onClick={onReset} disabled={!hasActiveFilters(filters)}>
          {fr.search.reset}
        </Button>
        {onApply ? (
          <Button type="primary" onClick={onApply}>
            {fr.search.filtersTitle}
          </Button>
        ) : null}
      </div>
    </div>
  );
}

export function CoursesPage() {
  useDocumentTitle(fr.search.title);
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const filters = useMemo(
    () => parseCatalogueSearch(searchParams.toString()),
    [searchParams],
  );
  const [term, setTerm] = useState(filters.q);
  const debouncedTerm = useDebouncedValue(term, SEARCH_DEBOUNCE_MS);
  const isMobile = useMediaQuery('(max-width: 899px)');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const categoriesQuery = useCategories();

  // Back and forward must restore the search field, not only the results.
  useEffect(() => {
    setTerm(filters.q);
  }, [filters.q]);

  useEffect(() => {
    const nextTerm = debouncedTerm.trim();
    // The debounced value lags behind an external change (reset, chip removal,
    // back button). Pushing it now would resurrect the previous query, so the
    // URL only follows the input once the debounce has caught up with it.
    if (nextTerm !== term.trim()) {
      return;
    }
    if (nextTerm === filters.q) {
      return;
    }
    setSearchParams(
      serializeCatalogueSearch({ ...filters, q: nextTerm, page: 1 }),
      { replace: true },
    );
  }, [debouncedTerm, term, filters, setSearchParams]);

  const update = (patch: Partial<CatalogueFilters>, replace = false) => {
    const next: CatalogueFilters = { ...filters, ...patch };
    if (patch.page === undefined) {
      next.page = 1;
    }
    // Keep the input in step with an external query change (chip removal,
    // navigated link). Otherwise the pending debounced term would push the old
    // query back into the address right after the removal.
    if (patch.q !== undefined) {
      setTerm(patch.q);
    }
    setSearchParams(serializeCatalogueSearch(next), { replace });
  };

  const reset = () => {
    setTerm('');
    setSearchParams('', { replace: true });
  };

  const queryParams: CourseSearchParams = {
    page: filters.page - 1,
    size: CATALOGUE_PAGE_SIZE,
    sort: filters.sort,
    ...(filters.q ? { q: filters.q } : {}),
    ...(filters.category ? { category: filters.category } : {}),
    ...(filters.tag ? { tag: filters.tag } : {}),
    ...(filters.level ? { level: filters.level } : {}),
    ...(filters.free ? { free: true } : {}),
  };
  const query = useCourseSearch(queryParams);
  const categories = categoriesQuery.data ?? [];

  const chips: FilterChip[] = [];
  if (filters.q) {
    chips.push({ key: 'q', label: fr.search.chipQuery(filters.q), patch: { q: '' } });
  }
  if (filters.category) {
    chips.push({
      key: 'category',
      label: fr.search.chipCategory(
        categories.find((category) => category.slug === filters.category)?.name ??
          filters.category,
      ),
      patch: { category: '' },
    });
  }
  if (filters.tag) {
    chips.push({ key: 'tag', label: fr.search.chipTag(filters.tag), patch: { tag: '' } });
  }
  if (filters.level) {
    chips.push({
      key: 'level',
      label: fr.search.chipLevel(fr.levels[filters.level]),
      patch: { level: '' },
    });
  }
  if (filters.free) {
    chips.push({ key: 'free', label: fr.search.chipFree, patch: { free: false } });
  }

  const panel = (
    <FilterPanel
      filters={filters}
      categories={categories}
      onChange={(patch) => update(patch)}
      onReset={reset}
      onApply={isMobile ? () => setDrawerOpen(false) : undefined}
    />
  );

  return (
    <main className="sanaa-page">
      <Button
        type="text"
        icon={<IconLeft />}
        className="sanaa-back"
        onClick={() => navigate(-1)}
      >
        {fr.search.back}
      </Button>

      <header className="sanaa-page__head">
        <h1 className="sanaa-title-xl">{fr.search.title}</h1>
        <p className="sanaa-text sanaa-muted">{fr.search.subtitle}</p>
      </header>

      <div className="sanaa-search-bar">
        <Input
          size="large"
          value={term}
          onChange={(value) => setTerm(value)}
          placeholder={fr.search.searchPlaceholder}
          aria-label={fr.search.searchLabel}
          allowClear
        />
        <span className="sanaa-caption">{fr.search.searchHint}</span>
      </div>

      <div className="sanaa-catalogue">
        {isMobile ? null : (
          <aside className="sanaa-catalogue__aside" aria-label={fr.search.filtersTitle}>
            <h2 className="sanaa-title-md">{fr.search.filtersTitle}</h2>
            {panel}
          </aside>
        )}

        <section className="sanaa-catalogue__results" aria-live="polite">
          <div className="sanaa-results-head">
            <p className="sanaa-text">
              {query.data ? fr.search.resultCount(query.data.totalElements) : ''}
            </p>
            {isMobile ? (
              <Button icon={<IconFilter />} onClick={() => setDrawerOpen(true)}>
                {fr.search.openFilters}
              </Button>
            ) : null}
          </div>

          {chips.length > 0 ? (
            <div className="sanaa-chips" aria-label={fr.search.activeFilters}>
              {chips.map((chip) => (
                <Tag
                  key={chip.key}
                  closable
                  closeIcon={
                    <span role="img" aria-label={fr.search.removeChip(chip.label)}>
                      <IconClose />
                    </span>
                  }
                  onClose={() => update(chip.patch)}
                >
                  {chip.label}
                </Tag>
              ))}
            </div>
          ) : null}

          {query.isPending ? (
            <div className="sanaa-course-grid" data-testid="catalogue-skeleton" aria-hidden="true">
              {Array.from({ length: 8 }, (_, index) => (
                <Card key={index} bordered className="sanaa-course-card">
                  <Skeleton animation text={{ rows: 4 }} />
                </Card>
              ))}
            </div>
          ) : query.isError ? (
            <Card bordered className="sanaa-card sanaa-error-card">
              <p className="sanaa-title-md">{fr.search.loadErrorTitle}</p>
              <p className="sanaa-text sanaa-muted">{fr.search.loadErrorBody}</p>
              <Button type="primary" onClick={() => void query.refetch()}>
                {fr.common.retry}
              </Button>
            </Card>
          ) : query.data.items.length === 0 ? (
            <div className="sanaa-empty">
              <Empty
                description={
                  <span>
                    <strong className="sanaa-empty__title">{fr.search.emptyTitle}</strong>
                    <span className="sanaa-caption">{fr.search.emptyBody}</span>
                  </span>
                }
              />
              {hasActiveFilters(filters) ? (
                <Button className="sanaa-empty__action" onClick={reset}>
                  {fr.search.reset}
                </Button>
              ) : null}
            </div>
          ) : (
            <>
              <div className="sanaa-course-grid">
                {query.data.items.map((course) => (
                  <CourseCard key={course.id} course={course} />
                ))}
              </div>
              {query.data.totalPages > 1 ? (
                <div className="sanaa-pagination">
                  <Pagination
                    current={filters.page}
                    pageSize={CATALOGUE_PAGE_SIZE}
                    total={query.data.totalElements}
                    onChange={(nextPage) => {
                      update({ page: nextPage });
                      window.scrollTo({ top: 0, behavior: 'smooth' });
                    }}
                    showTotal
                  />
                </div>
              ) : null}
            </>
          )}
        </section>
      </div>

      <Drawer
        visible={drawerOpen}
        title={fr.search.filtersTitle}
        placement="right"
        width={320}
        footer={null}
        onCancel={() => setDrawerOpen(false)}
      >
        {panel}
      </Drawer>
    </main>
  );
}
