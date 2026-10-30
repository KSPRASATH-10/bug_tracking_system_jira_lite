import { useRef, useState, type ChangeEvent, type DragEvent } from 'react';
import { Download, File as FileIcon, FileImage, FileText, Loader2, Trash2, Upload } from 'lucide-react';
import { ErrorState } from '@/components/ErrorState';
import { LoadingBlock } from '@/components/Spinner';
import { api, attachmentContentUrl, errorMessage } from '@/lib/api';
import { formatBytes, formatRelative, formatUtc } from '@/lib/format';
import { LIMITS } from '@/types/api';
import type { Attachment } from '@/types/api';

// Client-side mirror of FileValidationService. It is only a convenience: the server re-checks size, extension,
// magic bytes and declared MIME type, and is the authority.
const ALLOWED_EXTENSIONS = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'pdf', 'txt', 'log', 'csv', 'json', 'md'];
const BLOCKED_EXTENSIONS = new Set([
  'sh', 'bash', 'zsh', 'csh', 'ksh', 'exe', 'dll', 'bat', 'cmd', 'com', 'msi', 'scr', 'lnk', 'hta',
  'jsp', 'jspx', 'php', 'phtml', 'asp', 'aspx', 'cgi', 'html', 'htm', 'xhtml', 'svg', 'xml',
  'js', 'mjs', 'jar', 'war', 'class', 'py', 'pl', 'rb', 'ps1', 'psm1', 'vbs', 'wsf', 'apk', 'dmg',
]);
const MAX_ATTACHMENTS = 25;
const ACCEPT = ALLOWED_EXTENSIONS.map((extension) => `.${extension}`).join(',');

function validateFile(file: File): string | null {
  if (file.size === 0) {
    return `${file.name}: the file is empty`;
  }
  if (file.size > LIMITS.fileBytes) {
    return `${file.name}: larger than the ${formatBytes(LIMITS.fileBytes)} limit`;
  }
  const lower = file.name.toLowerCase();
  const parts = lower.split('.');
  if (lower.startsWith('.') || parts.length < 2 || parts[parts.length - 1] === '') {
    return `${file.name}: the file needs an allowed extension`;
  }
  // Every extension segment is checked, so "shell.sh.png" and "page.html.txt" are refused as the server does.
  if (parts.slice(1).some((part) => BLOCKED_EXTENSIONS.has(part))) {
    return `${file.name}: this file type is not allowed`;
  }
  if (!ALLOWED_EXTENSIONS.includes(parts[parts.length - 1])) {
    return `${file.name}: allowed types are ${ALLOWED_EXTENSIONS.join(', ')}`;
  }
  return null;
}

function iconFor(contentType: string) {
  if (contentType.startsWith('image/')) return FileImage;
  if (contentType.startsWith('text/') || contentType === 'application/pdf' || contentType === 'application/json') {
    return FileText;
  }
  return FileIcon;
}

interface AttachmentDropzoneProps {
  projectId: string;
  bugId: string;
  attachments: Attachment[] | null;
  loading: boolean;
  error: unknown;
  canUpload: boolean;
  canRemove: (attachment: Attachment) => boolean;
  onChanged: () => void;
  onRetry: () => void;
}

export function AttachmentDropzone({
  projectId,
  bugId,
  attachments,
  loading,
  error,
  canUpload,
  canRemove,
  onChanged,
  onRetry,
}: AttachmentDropzoneProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const [progress, setProgress] = useState<{ done: number; total: number } | null>(null);
  const [problems, setProblems] = useState<string[]>([]);
  const [confirmingId, setConfirmingId] = useState<string | null>(null);
  const [removingId, setRemovingId] = useState<string | null>(null);
  const uploading = progress !== null;

  async function uploadFiles(files: File[]) {
    if (files.length === 0 || uploading) {
      return;
    }
    const found: string[] = [];
    const valid: File[] = [];
    for (const file of files) {
      const problem = validateFile(file);
      if (problem) {
        found.push(problem);
      } else {
        valid.push(file);
      }
    }
    const room = Math.max(0, MAX_ATTACHMENTS - (attachments?.length ?? 0));
    if (valid.length > room) {
      found.push(`A bug can have at most ${MAX_ATTACHMENTS} attachments`);
      valid.length = room;
    }
    setProblems(found);
    if (valid.length === 0) {
      return;
    }

    setProgress({ done: 0, total: valid.length });
    const failures = [...found];
    for (let index = 0; index < valid.length; index += 1) {
      try {
        await api.attachments.upload(projectId, bugId, valid[index]);
      } catch (failure) {
        failures.push(`${valid[index].name}: ${errorMessage(failure, 'the upload failed')}`);
      }
      setProgress({ done: index + 1, total: valid.length });
    }
    setProgress(null);
    setProblems(failures);
    onChanged();
  }

  function handleInput(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? []);
    event.target.value = ''; // lets the same file be chosen again
    void uploadFiles(files);
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setDragging(false);
    if (canUpload) {
      void uploadFiles(Array.from(event.dataTransfer.files));
    }
  }

  async function remove(attachment: Attachment) {
    setRemovingId(attachment.id);
    try {
      await api.attachments.archive(projectId, bugId, attachment.id);
      setConfirmingId(null);
      onChanged();
    } catch (failure) {
      setProblems([`${attachment.filename}: ${errorMessage(failure, 'it could not be removed')}`]);
      setConfirmingId(null);
    } finally {
      setRemovingId(null);
    }
  }

  return (
    <div>
      {canUpload && (
        <div className="px-4 pt-4">
          <div
            onDragOver={(event) => {
              event.preventDefault();
              setDragging(true);
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={handleDrop}
            className={`flex flex-col items-center justify-center rounded-lg border-2 border-dashed px-4 py-5 text-center transition-colors ${
              dragging ? 'border-brand bg-brand-50' : 'border-line bg-surface'
            }`}
          >
            {uploading ? (
              <span role="status" className="flex items-center gap-2 text-sm text-slate-700">
                <Loader2 className="h-4 w-4 animate-spin text-brand" aria-hidden />
                Uploading {Math.min((progress?.done ?? 0) + 1, progress?.total ?? 1)} of {progress?.total}…
              </span>
            ) : (
              <>
                <Upload className="h-5 w-5 text-slate-500" aria-hidden />
                <p className="mt-1.5 text-sm text-ink">Drag files here, or</p>
                <button type="button" className="btn-secondary mt-2" onClick={() => inputRef.current?.click()}>
                  Choose files
                </button>
                <p className="hint">
                  Images, PDF and plain text &middot; up to {formatBytes(LIMITS.fileBytes)} each &middot; {MAX_ATTACHMENTS} per
                  bug
                </p>
              </>
            )}
            <input
              ref={inputRef}
              type="file"
              multiple
              accept={ACCEPT}
              className="sr-only"
              tabIndex={-1}
              aria-label="Choose files to attach"
              onChange={handleInput}
            />
          </div>
        </div>
      )}

      {problems.length > 0 && (
        <div role="alert" className="mx-4 mt-3 rounded-md border border-sev-critical/30 bg-sev-critical-tint px-3 py-2">
          <p className="text-xs font-semibold text-sev-critical-ink">Some files were not attached:</p>
          <ul className="mt-1 list-disc space-y-0.5 pl-4 text-xs text-sev-critical-ink">
            {problems.map((problem, index) => (
              <li key={`${index}-${problem}`} className="break-words">
                {problem}
              </li>
            ))}
          </ul>
        </div>
      )}

      {error && !attachments ? (
        <ErrorState error={error} title="Attachments could not be loaded" onRetry={onRetry} />
      ) : loading && !attachments ? (
        <LoadingBlock label="Loading attachments…" rows={2} />
      ) : attachments && attachments.length === 0 ? (
        <p className="px-4 py-4 text-xs text-slate-500">No attachments yet.</p>
      ) : (
        <ul className="mt-3 divide-y divide-line border-t border-line">
          {(attachments ?? []).map((attachment) => {
            const Icon = iconFor(attachment.contentType);
            const removable = canRemove(attachment);
            return (
              <li key={attachment.id} className="flex items-center gap-3 px-4 py-2.5">
                <Icon className="h-5 w-5 flex-none text-slate-500" aria-hidden />
                <div className="min-w-0 flex-1">
                  {/* The server always answers with "Content-Disposition: attachment" + nosniff, so this saves the file. */}
                  <a
                    href={attachmentContentUrl(projectId, bugId, attachment.id)}
                    download={attachment.filename}
                    className="block truncate text-sm font-medium text-brand-700 hover:underline"
                  >
                    {attachment.filename}
                  </a>
                  <p className="text-xs text-slate-500">
                    <span title={`SHA-256 ${attachment.sha256}`}>{formatBytes(attachment.sizeBytes)}</span>
                    {' \u00B7 '}
                    {attachment.uploader.fullName}
                    {' \u00B7 '}
                    <span title={formatUtc(attachment.createdAt)}>{formatRelative(attachment.createdAt)}</span>
                  </p>
                </div>
                <a
                  href={attachmentContentUrl(projectId, bugId, attachment.id)}
                  download={attachment.filename}
                  className="btn-icon"
                  aria-label={`Download ${attachment.filename}`}
                >
                  <Download className="h-4 w-4" aria-hidden />
                </a>
                {removable &&
                  (confirmingId === attachment.id ? (
                    <span className="flex items-center gap-1">
                      <button
                        type="button"
                        className="btn-danger px-2 py-1 text-xs"
                        disabled={removingId === attachment.id}
                        onClick={() => void remove(attachment)}
                      >
                        {removingId === attachment.id ? 'Removing…' : 'Confirm'}
                      </button>
                      <button
                        type="button"
                        className="btn-ghost px-2 py-1 text-xs"
                        disabled={removingId === attachment.id}
                        onClick={() => setConfirmingId(null)}
                      >
                        Cancel
                      </button>
                    </span>
                  ) : (
                    <button
                      type="button"
                      className="btn-icon"
                      aria-label={`Remove ${attachment.filename}`}
                      onClick={() => setConfirmingId(attachment.id)}
                    >
                      <Trash2 className="h-4 w-4" aria-hidden />
                    </button>
                  ))}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
