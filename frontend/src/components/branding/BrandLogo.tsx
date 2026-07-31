import Image from 'next/image';

type BrandLogoProps = {
  src: string;
  alt: string;
  size: number;
  borderRadius: number;
  priority?: boolean;
  className?: string;
};

export function BrandLogo({
  src,
  alt,
  size,
  borderRadius,
  priority = false,
  className = '',
}: BrandLogoProps) {
  return (
    <span
      data-testid="brand-logo-frame"
      className={`inline-flex shrink-0 overflow-hidden ${className}`}
      style={{ borderRadius: `${borderRadius}px` }}
    >
      <Image
        src={src}
        alt={alt}
        width={size}
        height={size}
        priority={priority}
        className="h-full w-full object-contain"
      />
    </span>
  );
}
