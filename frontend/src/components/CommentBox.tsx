import { useState, type FormEvent, type KeyboardEvent } from 'react';
import { Loader2 } from 'lucide-react';
import { api, errorMessage } from '@/lib/api';
import { LIMITS } from '@/types/api';

interface CommentBoxProps {
  projectId: string;
  bugId: string;
  disabled?: boolean;
  disabledReason?: string;
  onAdded: () => void;
}

/** Append-only comment form: there is no way to edit or delete a comment after posting. */
export function CommentBox({ projectId, bugId, disabled = false, disabledReason, onAdded }: CommentBoxProps) {
  const [body, setBody] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    if (busy) {
      return;
    }
    if (!body.trim()) {
      setError('Write a comment before posting');
      return;
    }
    if (body.length > LIMITS.comment) {
      setError(`Comment must be at most ${LIMITS.comment.toLocaleString()} characters`);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api.comments.add(projectId, bugId, body);
      setBody('');
      onAdded();
    } catch (failure) {
      setError(errorMessage(failure, 'The comment could not be posted.'));
    } finally {
      setBusy(false);
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void submit();
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
      event.preventDefault();
      void submit();
    }
  }

  if (disabled) {
    return <p className="text-xs text-slate-500">{disabledReason ?? 'You cannot comment on this bug.'}</p>;
  }

  const over = body.length > LIMITS.comment;
  return (
    <form onSubmit={handleSubmit} noValidate>
      <div className="flex items-end justify-between">
        <label htmlFor="comment-body" className="label">
          Add a comment
        </label>
        <span className={`text-[11px] tabular-nums ${over ? 'font-semibold text-sev-critical-ink' : 'text-slate-500'}`}>
          {body.length.toLocaleString()}/{LIMITS.comment.toLocaleString()}
        </span>
      </div>
      <textarea
        id="comment-body"
        className="input min-h-[6rem] resize-y"
        value={body}
        onChange={(event) => setBody(event.target.value)}
        onKeyDown={handleKeyDown}
        disabled={busy}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? 'comment-error' : 'comment-hint'}
      />
      {error ? (
        <p id="comment-error" role="alert" className="field-error">
          {error}
        </p>
      ) : (
        <p id="comment-hint" className="hint">
          Markdown is supported; raw HTML is never rendered. Comments cannot be edited or deleted. Press Ctrl+Enter to post.
        </p>
      )}
      <div className="mt-2 flex justify-end">
        <button type="submit" className="btn-primary" disabled={busy || body.trim().length === 0}>
          {busy && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
          {busy ? 'Posting…' : 'Post comment'}
        </button>
      </div>
    </form>
  );
}
