import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { BrandLogo } from './BrandLogo';

describe('BrandLogo', () => {
  it('renders the mark inline with an accessible name and the brand color current', () => {
    const { container } = render(<BrandLogo variant="mark" height={40} />);
    const svg = screen.getByRole('img', { name: 'SANAA' });

    expect(svg.tagName.toLowerCase()).toBe('svg');
    expect(svg).toHaveClass('sanaa-logo', 'sanaa-logo--mark');
    expect(svg).toHaveAttribute('height', '40');
    expect(svg.getAttribute('viewBox')).toBe('0 0 1077 616');

    const path = svg.querySelector('path');
    expect(path).not.toBeNull();
    expect(path).toHaveAttribute('fill', 'currentColor');

    // The logo must never require a network request.
    expect(container.querySelector('img')).toBeNull();
    expect(container.querySelector('image')).toBeNull();
    expect(container.innerHTML).not.toContain('http');
  });

  it('uses the full lockup viewBox for the full variant', () => {
    render(<BrandLogo variant="full" height={88} />);
    const svg = screen.getByRole('img', { name: 'SANAA' });

    expect(svg).toHaveClass('sanaa-logo--full');
    expect(svg).toHaveAttribute('height', '88');
    expect(svg.getAttribute('viewBox')).toBe('0 0 1084 1009');
  });

  it('never renders the registered trademark symbol', () => {
    const { container } = render(<BrandLogo variant="full" />);
    expect(container.textContent).toBe('SANAA');
    expect(container.textContent).not.toContain('®');
  });
});
