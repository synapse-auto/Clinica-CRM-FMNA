import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { BrandLogo } from './BrandLogo';

describe('BrandLogo', () => {
  it('clips only the logo using the configured border radius', () => {
    const { rerender } = render(
      <BrandLogo
        src="/fmna-logo.png"
        alt="FMNA"
        size={48}
        borderRadius={12}
        className="h-12 w-12"
      />,
    );

    const frame = screen.getByTestId('brand-logo-frame');
    expect(frame).toHaveStyle({ borderRadius: '12px' });
    expect(frame).toHaveClass('overflow-hidden', 'h-12', 'w-12');
    expect(screen.getByAltText('FMNA')).toHaveClass('object-contain');

    rerender(
      <BrandLogo
        src="/ultramedical-logo.png"
        alt="UltraMedical"
        size={48}
        borderRadius={0}
      />,
    );

    expect(frame).toHaveStyle({ borderRadius: '0px' });
    expect(screen.getByAltText('UltraMedical')).toBeInTheDocument();
  });
});
