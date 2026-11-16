import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { PriceBlock } from './CourseCard';
import { renderWithProviders } from '../test/utils';

const base = {
  price: 1200,
  effectivePrice: 1200,
  currency: 'MAD',
  discountActive: false,
};

describe('PriceBlock', () => {
  it('prints a free course as "Gratuit"', () => {
    renderWithProviders(
      <PriceBlock course={{ ...base, price: 0, effectivePrice: 0 }} />,
    );
    expect(screen.getByText('Gratuit')).toBeInTheDocument();
  });

  it('formats a paid amount through Intl with the French comma', () => {
    renderWithProviders(<PriceBlock course={base} />);
    expect(screen.getByText(/1\s200,00\sMAD/)).toBeInTheDocument();
  });

  it('shows the original price and the discount badge of an active promotion', () => {
    renderWithProviders(
      <PriceBlock
        course={{ ...base, price: 1200, effectivePrice: 900, discountActive: true }}
      />,
    );
    expect(screen.getByText(/900,00\sMAD/)).toBeInTheDocument();
    expect(screen.getByText(/-25 %/)).toBeInTheDocument();
    expect(screen.getByText(/1\s200,00\sMAD/)).toBeInTheDocument();
  });

  it('never shows a stale discount once the promotion expired', () => {
    renderWithProviders(
      <PriceBlock
        course={{ ...base, price: 1200, effectivePrice: 900, discountActive: false }}
      />,
    );
    expect(screen.getByText(/1\s200,00\sMAD/)).toBeInTheDocument();
    expect(screen.queryByText(/-25 %/)).not.toBeInTheDocument();
  });

  it('hides the struck-through price from assistive technology', () => {
    const { container } = renderWithProviders(
      <PriceBlock
        course={{ ...base, price: 1200, effectivePrice: 900, discountActive: true }}
      />,
    );
    const original = container.querySelector('.sanaa-price__original');
    expect(original).toHaveAttribute('aria-hidden', 'true');
  });
});
