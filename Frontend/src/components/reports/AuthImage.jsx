import { useEffect, useState } from 'react';
import { ImageOff } from 'lucide-react';
import { reportService } from '../../services/reportService.js';

export default function AuthImage({ id, alt, className = '' }) {
  const [src, setSrc] = useState(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let url;
    let cancelled = false;
    reportService
      .image(id)
      .then((blob) => {
        if (cancelled) return;
        url = URL.createObjectURL(blob);
        setSrc(url);
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      });
    return () => {
      cancelled = true;
      if (url) URL.revokeObjectURL(url);
    };
  }, [id]);

  if (failed) {
    return (
      <div className={`flex items-center justify-center bg-paper-200 text-ink-400 ${className}`}>
        <ImageOff className="h-5 w-5" aria-label="Photo unavailable" />
      </div>
    );
  }
  if (!src) return <div className={`animate-pulse bg-paper-200 ${className}`} aria-hidden />;
  return <img src={src} alt={alt} className={`object-cover ${className}`} />;
}