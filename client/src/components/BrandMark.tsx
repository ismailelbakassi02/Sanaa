interface BrandMarkProps {
  name: string;
  logoUrl?: string | null;
  large?: boolean;
}

function isUsableLogoUrl(url: string | null | undefined): url is string {
  if (!url) {
    return false;
  }
  return (
    url.startsWith('https://') ||
    url.startsWith('http://') ||
    url.startsWith('/') ||
    url.startsWith('data:image/')
  );
}

export function BrandMark({ name, logoUrl, large = false }: BrandMarkProps) {
  const className = large ? 'sanaa-brand-mark sanaa-brand-mark--large' : 'sanaa-brand-mark';

  if (isUsableLogoUrl(logoUrl)) {
    return (
      <span className={className}>
        <img className="sanaa-brand-mark__image" src={logoUrl} alt="" />
      </span>
    );
  }

  const initial = name.trim().charAt(0).toUpperCase() || 'S';
  return (
    <span className={className} aria-hidden="true">
      <span className="sanaa-brand-mark__initial">{initial}</span>
    </span>
  );
}
