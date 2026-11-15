import { Button, Message, Upload } from '@arco-design/web-react';
import { IconClose, IconUpload } from '@arco-design/web-react/icon';

import { messageForError } from '../api/messages';
import { publicImageUrl } from '../api/profile';
import { useUploadImage } from '../hooks/profile';
import { fr } from '../i18n/fr';

const MAX_BYTES = 1024 * 1024;
const ALLOWED_TYPES = ['image/png', 'image/jpeg'];

interface ImageUploadFieldProps {
  kind: 'avatar' | 'logo';
  value: string | null;
  onChange: (imageId: string | null) => void;
  disabled?: boolean;
}

/**
 * Avatar and logo upload with a client-side size/type pre-check. The backend
 * repeats both checks from the magic bytes, which is the authority.
 */
export function ImageUploadField({ kind, value, onChange, disabled = false }: ImageUploadFieldProps) {
  const upload = useUploadImage();
  const preview = publicImageUrl(value);

  const handleFile = async (file: File) => {
    if (!ALLOWED_TYPES.includes(file.type)) {
      Message.error(fr.images.wrongType);
      return;
    }
    if (file.size > MAX_BYTES) {
      Message.error(fr.images.tooLarge);
      return;
    }
    try {
      const result = await upload.mutateAsync({ kind, file });
      onChange(result.id);
      Message.success(fr.images.uploaded);
    } catch (error) {
      Message.error(messageForError(error));
    }
  };

  return (
    <div className="sanaa-image-field">
      <div className="sanaa-image-field__preview" data-kind={kind}>
        {preview ? <img src={preview} alt="" /> : <span aria-hidden="true">{fr.images.placeholder}</span>}
      </div>
      <div className="sanaa-image-field__actions">
        <Upload
          accept="image/png,image/jpeg"
          showUploadList={false}
          autoUpload={false}
          disabled={disabled || upload.isPending}
          beforeUpload={(file) => {
            void handleFile(file);
            // The component handles the request itself through the shared
            // axios client, so Arco must not start its own upload.
            return false;
          }}
        >
          <Button icon={<IconUpload />} loading={upload.isPending} disabled={disabled}>
            {value ? fr.images.replace : fr.images.choose}
          </Button>
        </Upload>
        {value && !disabled ? (
          <Button
            type="text"
            icon={<IconClose />}
            onClick={() => onChange(null)}
            aria-label={fr.images.remove}
          >
            {fr.images.remove}
          </Button>
        ) : null}
      </div>
    </div>
  );
}
