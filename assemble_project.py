#!/usr/bin/env python3
"""
Rebuilds the complete BNY Jira Lite repository from the saved conversation transcript and packages it.

Usage
  1. Save every assistant reply, in order, as Markdown into one file (default name: conversation.md).
     The Copy button on each reply normally copies Markdown with the code fences intact.
  2. python3 assemble_project.py --input conversation.md

What it does
  * finds every fenced code block that identifies a repository path, either through its first line (a
    "// path", "# path", "-- path", "/* path */" or "<!-- path -->" comment) or through the "File N of 136"
    heading directly above the block, and strips that path comment from the written file;
  * accepts only safe, relative paths under backend/, frontend/, docs/ or a short list of root files;
  * applies the three documented amendments (pom.xml, lib/api.ts, BugDetailPage.tsx), idempotently;
  * checks the result against the 136-file manifest and runs cheap integrity checks (Java package and type
    name against the path, package.json parses);
  * writes the tree, a SHA256SUMS.txt and bny-jira-lite-production.zip.

Exit codes: 0 success, 1 usage or I/O error, 2 files missing from the transcript (nothing written).
Options: --check-only (report only), --force, --allow-partial, --strict (integrity warnings fail the run).
"""
import argparse
import hashlib
import json
import re
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Dict, List, Optional, Tuple

PROJECT_DIR = "bny-jira-lite"
DEFAULT_ZIP = "bny-jira-lite-production.zip"
EXPECTED_FILE_COUNT = 136


def _expand(prefix: str, names: str, suffix: str) -> List[str]:
    return [prefix + name + suffix for name in names.split()]


_MAIN = "backend/src/main/java/com/bny/bts/"
_TEST = "backend/src/test/java/com/bny/bts/"
_WEB = "frontend/src/"

# The Global File Execution Manifest: every path the generation promised, in ledger order.
MANIFEST: List[str] = [
    "docker-compose.yml",
    ".env.example",
    "backend/src/main/resources/db/migration/V1__init_schema.sql",
    "backend/src/main/resources/db/migration/V2__seed_test_data.sql",
    "backend/pom.xml",
    _MAIN + "BtsApplication.java",
    "backend/src/main/resources/application.yml",
    "backend/src/test/resources/application-test.yml",
    _MAIN + "config/BtsProperties.java",
    *_expand(_MAIN + "domain/",
             "SystemRole ProjectRole BugStatus BugPriority Resolution HistoryEventType "
             "User Project ProjectMemberId ProjectMember Bug BugComment BugHistory Attachment AuditLog", ".java"),
    *_expand(_MAIN + "repository/",
             "UserRepository ProjectRepository ProjectMemberRepository BugRepository BugCommentRepository "
             "BugHistoryRepository AttachmentRepository AuditLogRepository BugSpecifications", ".java"),
    *_expand(_MAIN + "web/dto/",
             "LoginRequest UserResponse CreateProjectRequest ProjectResponse AddMemberRequest MemberResponse "
             "CreateBugRequest UpdateBugRequest TransitionRequest AssignRequest BugResponse BugSummaryResponse "
             "PageResponse CommentRequest CommentResponse TimelineEntryResponse AttachmentResponse ApiError "
             "DashboardResponse", ".java"),
    *_expand(_MAIN + "security/",
             "AppUserPrincipal UserDetailsServiceImpl JwtService AuthCookieService JwtAuthenticationFilter "
             "ProjectSecurity LoginRateLimitFilter", ".java"),
    _MAIN + "config/SecurityConfig.java",
    *_expand(_MAIN + "web/filter/", "CorrelationIdFilter SecurityHeadersFilter", ".java"),
    *_expand(_MAIN + "service/",
             "BugStateMachine MarkdownSanitizer AuditService AuthService ProjectService BugService CommentService "
             "TimelineService FileValidationService AttachmentService DashboardService", ".java"),
    *_expand(_MAIN + "exception/", "NotFoundException InvalidTransitionException FileRejectedException", ".java"),
    _MAIN + "web/GlobalExceptionHandler.java",
    _MAIN + "config/OpenApiConfig.java",
    *_expand(_MAIN + "web/controller/",
             "AuthController ProjectController BugController CommentController AttachmentController "
             "DashboardController", ".java"),
    *_expand(_TEST + "service/", "BugStateMachineTest MarkdownSanitizerTest FileValidationServiceTest", ".java"),
    _TEST + "security/LoginRateLimitFilterTest.java",
    *_expand(_TEST + "integration/",
             "AbstractIntegrationTest AuthIntegrationTest BolaSecurityIntegrationTest BugWorkflowIntegrationTest "
             "InjectionDefenseIntegrationTest OptimisticLockingIntegrationTest", ".java"),
    "frontend/package.json",
    "frontend/vite.config.ts",
    "frontend/tsconfig.json",
    "frontend/tsconfig.node.json",
    "frontend/tailwind.config.js",
    "frontend/postcss.config.js",
    "frontend/index.html",
    "frontend/.env.example",
    _WEB + "main.tsx",
    _WEB + "index.css",
    _WEB + "types/api.ts",
    _WEB + "lib/api.ts",
    _WEB + "lib/format.ts",
    _WEB + "context/AuthContext.tsx",
    *_expand(_WEB + "components/",
             "ProtectedRoute AppShell Breadcrumbs StatusPill PriorityBadge Spinner EmptyState ErrorState "
             "Pagination Modal", ".tsx"),
    *_expand(_WEB + "pages/", "LoginPage DashboardPage ProjectsPage BugListPage", ".tsx"),
    _WEB + "components/CreateBugModal.tsx",
    _WEB + "pages/BugDetailPage.tsx",
    *_expand(_WEB + "components/",
             "StatusTransition ResolutionModal AssigneeSelect ActivityTimeline CommentBox AttachmentDropzone", ".tsx"),
    _WEB + "App.tsx",
    ".gitignore",
    "README.md",
    "docs/SECURITY.md",
    "docs/ARCHITECTURE.md",
    "assemble_project.py",
]

ROOT_FILES = frozenset({"docker-compose.yml", ".env.example", ".gitignore", "README.md", "assemble_project.py"})
ALLOWED_PREFIXES = ("backend/", "frontend/", "docs/")
_SAFE_PATH = re.compile(r"[A-Za-z0-9._@/-]+")
FENCE_RE = re.compile(r"^ {0,3}(`{3,}|~{3,})(.*)$")
HEADER_RE = re.compile(r"^\*\*File\s+(\d+)\s+of\s+136(?:\s*,\s*`([^`]+)`)?\s*\*\*$")
COMMENT_PATTERNS = (
    re.compile(r"^//\s*(\S+)$"),
    re.compile(r"^#\s*(\S+)$"),
    re.compile(r"^--\s*(\S+)$"),
    re.compile(r"^/\*\s*(\S+)\s*\*/$"),
    re.compile(r"^<!--\s*(\S+)\s*-->$"),
)
JAVA_TYPE_RE = re.compile(r"^(?:public\s+|abstract\s+|final\s+|sealed\s+)*(?:class|interface|enum|record)\s+(\w+)",
                          re.MULTILINE)
JAVA_PACKAGE_RE = re.compile(r"^package\s+([\w.]+);", re.MULTILINE)


# ------------------------------------------------------------------------------------------------
# Transcript parsing
# ------------------------------------------------------------------------------------------------

@dataclass
class Block:
    line_no: int
    lines: List[str]
    header_path: Optional[str]
    terminated: bool


def fail(message: str, code: int = 1) -> None:
    print("error: " + message, file=sys.stderr)
    sys.exit(code)


def read_transcript(path: Path) -> str:
    try:
        raw = path.read_bytes()
    except OSError as exc:
        fail("cannot read " + str(path) + ": " + str(exc))
    text = raw.decode("utf-8-sig", errors="replace")
    if "\ufffd" in text:
        print("warning: the transcript contains bytes that are not valid UTF-8; affected characters were replaced",
              file=sys.stderr)
    return text.replace("\r\n", "\n").replace("\r", "\n")


def _preceding_header_path(lines: List[str], fence_index: int) -> Optional[str]:
    k = fence_index - 1
    while k >= 0 and not lines[k].strip():
        k -= 1
    if k < 0:
        return None
    match = HEADER_RE.match(lines[k].strip())
    return match.group(2) if match else None


def extract_blocks(text: str) -> List[Block]:
    """CommonMark fences: a closing fence uses the same character, is at least as long, and has no info string."""
    lines = text.split("\n")
    blocks: List[Block] = []
    i = 0
    while i < len(lines):
        opening = FENCE_RE.match(lines[i])
        if not opening or (opening.group(1)[0] == "`" and "`" in opening.group(2)):
            i += 1
            continue
        fence = opening.group(1)
        char = fence[0]
        header_path = _preceding_header_path(lines, i)
        body: List[str] = []
        terminated = False
        j = i + 1
        while j < len(lines):
            closing = FENCE_RE.match(lines[j])
            if (closing and closing.group(1)[0] == char and len(closing.group(1)) >= len(fence)
                    and not closing.group(2).strip()):
                terminated = True
                break
            body.append(lines[j])
            j += 1
        blocks.append(Block(i + 1, body, header_path, terminated))
        i = j + 1
    return blocks


def is_project_path(path: str) -> bool:
    if path in ROOT_FILES:
        return True
    if not path.startswith(ALLOWED_PREFIXES) or not _SAFE_PATH.fullmatch(path):
        return False
    parts = path.split("/")
    return all(part not in ("", ".", "..") for part in parts) and "." in parts[-1]


def _path_from_comment(line: str) -> Optional[str]:
    for pattern in COMMENT_PATTERNS:
        match = pattern.match(line)
        if match:
            return match.group(1)
    return None


def resolve_block(block: Block, warnings: List[str]) -> Tuple[Optional[str], List[str]]:
    first = block.lines[0].strip() if block.lines else ""
    commented = _path_from_comment(first)
    if commented and is_project_path(commented):
        if block.header_path and block.header_path != commented:
            warnings.append("line %d: heading says %s but the block says %s; using the block's path"
                            % (block.line_no, block.header_path, commented))
        return commented, block.lines[1:]
    if block.header_path and is_project_path(block.header_path):
        return block.header_path, block.lines
    return None, block.lines


def collect_files(blocks: List[Block]) -> Tuple[Dict[str, Tuple[str, int]], List[str]]:
    files: Dict[str, Tuple[str, int]] = {}
    warnings: List[str] = []
    for block in blocks:
        path, body = resolve_block(block, warnings)
        if path is None:
            continue
        if not block.terminated:
            warnings.append("line %d: the block for %s is never closed (truncated paste?) and was ignored"
                            % (block.line_no, path))
            continue
        if path in files:
            warnings.append("line %d: %s appears more than once; the later copy wins" % (block.line_no, path))
        files[path] = ("\n".join(body).rstrip("\n") + "\n", block.line_no)
    return files, warnings


# ------------------------------------------------------------------------------------------------
# Documented amendments (idempotent)
# ------------------------------------------------------------------------------------------------

USE_API_BLOCK = r"""
// ---------------------------------------------------------------------------------------------
// Data-loading hook
// ---------------------------------------------------------------------------------------------

export interface AsyncState<T> {
  data: T | null;
  error: unknown;
  loading: boolean;
  /** Re-runs the loader and keeps the current data on screen while it runs. */
  reload: () => void;
}

interface UseApiOptions {
  /** Keep showing the previous result while the inputs change (pagination, filters) instead of a skeleton. */
  keepPreviousData?: boolean;
}

/**
 * Runs `loader` on mount and whenever `deps` change. In-flight requests are aborted on change/unmount, so a
 * slow response can never overwrite a newer one. An aborted request is never reported as an error.
 */
export function useApi<T>(
  loader: (signal: AbortSignal) => Promise<T>,
  deps: readonly unknown[],
  options: UseApiOptions = {},
): AsyncState<T> {
  const [state, setState] = useState<{ data: T | null; error: unknown; loading: boolean }>({
    data: null,
    error: null,
    loading: true,
  });
  const [reloadToken, setReloadToken] = useState(0);
  const loaderRef = useRef(loader);
  const depsRef = useRef<readonly unknown[]>(deps);
  const keepPrevious = options.keepPreviousData === true;

  useEffect(() => {
    loaderRef.current = loader;
  });

  useEffect(() => {
    const controller = new AbortController();
    const depsChanged =
      depsRef.current.length !== deps.length || deps.some((dep, index) => !Object.is(dep, depsRef.current[index]));
    depsRef.current = deps;

    // A plain reload keeps the data; changed inputs drop it unless the caller asked to keep it.
    setState((previous) => ({
      data: depsChanged && !keepPrevious ? null : previous.data,
      error: null,
      loading: true,
    }));

    loaderRef.current(controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) {
          setState({ data, error: null, loading: false });
        }
      },
      (error: unknown) => {
        if (controller.signal.aborted || isAbortError(error)) {
          return;
        }
        setState((previous) => ({ data: previous.data, error, loading: false }));
      },
    );
    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, reloadToken]);

  const reload = useCallback(() => setReloadToken((token) => token + 1), []);
  return { ...state, reload };
}
"""

REACT_HOOKS_IMPORT = "import { useCallback, useEffect, useRef, useState } from 'react';"


def _patch_pom(text: str) -> Tuple[str, str]:
    original = text
    text = re.sub(r"[ \t]*<owasp-sanitizer\.version>[^<]*</owasp-sanitizer\.version>[ \t]*\n", "", text)
    text = re.sub(r"[ \t]*<!--\s*Markdown/HTML content sanitisation\s*-->[ \t]*\n", "", text)
    text = re.sub(r"[ \t]*<dependency>\s*<groupId>com\.googlecode\.owasp-java-html-sanitizer</groupId>.*?</dependency>[ \t]*\n",
                  "", text, flags=re.DOTALL)
    return text, ("applied" if text != original else "already applied")


def _patch_api_ts(text: str) -> Tuple[str, str]:
    changed = False
    if REACT_HOOKS_IMPORT not in text:
        text = REACT_HOOKS_IMPORT + "\n" + text
        changed = True
    if "export function useApi" not in text:
        text = text.rstrip("\n") + "\n" + USE_API_BLOCK
        changed = True
    return text, ("applied" if changed else "already applied")


def _patch_bug_detail(text: str) -> Tuple[str, str]:
    original = text
    text = text.replace("import { useEffect, useState, type FormEvent } from 'react';",
                        "import { useEffect, useState, type FormEvent, type ReactNode } from 'react';")
    text = text.replace("children: React.ReactNode", "children: ReactNode")
    return text, ("applied" if text != original else "already applied")


PATCHES: Dict[str, Callable[[str], Tuple[str, str]]] = {
    "backend/pom.xml": _patch_pom,
    "frontend/src/lib/api.ts": _patch_api_ts,
    "frontend/src/pages/BugDetailPage.tsx": _patch_bug_detail,
}


def apply_patches(files: Dict[str, Tuple[str, int]]) -> List[str]:
    log: List[str] = []
    for path, patch in PATCHES.items():
        if path not in files:
            continue
        content, line_no = files[path]
        patched, status = patch(content)
        files[path] = (patched, line_no)
        log.append("%-45s %s" % (path, status))
    return log


# ------------------------------------------------------------------------------------------------
# Integrity checks
# ------------------------------------------------------------------------------------------------

def sanity_check(path: str, content: str) -> List[str]:
    issues: List[str] = []
    if not content.strip():
        issues.append("file is empty")
        return issues
    if path.endswith(".java") and "/java/" in path:
        relative = path.split("/java/", 1)[1]
        expected_package = ".".join(relative.split("/")[:-1])
        stem = relative.split("/")[-1][:-len(".java")]
        package = JAVA_PACKAGE_RE.search(content)
        if not package:
            issues.append("no package declaration")
        elif package.group(1) != expected_package:
            issues.append("package %s does not match its path (expected %s)" % (package.group(1), expected_package))
        declared = JAVA_TYPE_RE.search(content)
        if not declared:
            issues.append("no top-level type found")
        elif declared.group(1) != stem:
            issues.append("top-level type %s does not match the file name" % declared.group(1))
        if content.count("{") != content.count("}"):
            issues.append("unbalanced braces (truncated or damaged paste?)")
    if path == "frontend/package.json":
        try:
            json.loads(content)
        except ValueError as exc:
            issues.append("not valid JSON: " + str(exc))
    if path == "backend/pom.xml" and not content.lstrip().startswith("<?xml"):
        issues.append("pom.xml must start with the XML declaration")
    return issues


# ------------------------------------------------------------------------------------------------
# Output
# ------------------------------------------------------------------------------------------------

def _safe_target(out_dir: Path, relative: str) -> Path:
    root = out_dir.resolve()
    target = (root / relative).resolve()
    try:
        target.relative_to(root)
    except ValueError:
        fail("refusing to write outside the output directory: " + relative)
    return target


def write_tree(out_dir: Path, files: Dict[str, Tuple[str, int]]) -> List[str]:
    sums: List[str] = []
    for relative in sorted(files):
        data = files[relative][0].encode("utf-8")
        target = _safe_target(out_dir, relative)
        target.parent.mkdir(parents=True, exist_ok=True)
        with open(target, "wb") as handle:
            handle.write(data)
        sums.append("%s  %s" % (hashlib.sha256(data).hexdigest(), relative))
    with open(out_dir / "SHA256SUMS.txt", "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(sums) + "\n")
    return sums


def write_zip(out_dir: Path, zip_path: Path) -> int:
    count = 0
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(p for p in out_dir.rglob("*") if p.is_file()):
            archive.write(path, PROJECT_DIR + "/" + path.relative_to(out_dir).as_posix())
            count += 1
    return count


def parse_args(argv: Optional[List[str]]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Rebuild the BNY Jira Lite repository from a saved transcript.")
    parser.add_argument("--input", "-i", default="conversation.md", help="saved transcript (default: conversation.md)")
    parser.add_argument("--output-dir", "-o", default=PROJECT_DIR, help="directory to create (default: %(default)s)")
    parser.add_argument("--zip", dest="zip_path", default=DEFAULT_ZIP, help="zip file to create (default: %(default)s)")
    parser.add_argument("--check-only", action="store_true", help="parse and report; write nothing")
    parser.add_argument("--force", action="store_true", help="overwrite an existing output directory / zip")
    parser.add_argument("--allow-partial", action="store_true", help="write what was found even if files are missing")
    parser.add_argument("--strict", action="store_true", help="treat integrity warnings as failures")
    return parser.parse_args(argv)


def main(argv: Optional[List[str]] = None) -> int:
    args = parse_args(argv)
    if len(MANIFEST) != EXPECTED_FILE_COUNT or len(set(MANIFEST)) != len(MANIFEST):
        print("internal warning: the built-in manifest does not list %d unique files" % EXPECTED_FILE_COUNT,
              file=sys.stderr)

    text = read_transcript(Path(args.input))
    blocks = extract_blocks(text)
    files, warnings = collect_files(blocks)
    patch_log = apply_patches(files)

    problems: List[str] = []
    for path in sorted(files):
        for issue in sanity_check(path, files[path][0]):
            problems.append("%s: %s" % (path, issue))

    expected = set(MANIFEST)
    missing = [path for path in MANIFEST if path not in files]
    extra = sorted(path for path in files if path not in expected)

    print("Transcript : %s (%d code blocks, %d repository files recognised)" % (args.input, len(blocks), len(files)))
    print("Manifest   : %d of %d expected files found" % (len(MANIFEST) - len(missing), len(MANIFEST)))
    for line in patch_log:
        print("Amendment  : " + line)
    for warning in warnings:
        print("warning    : " + warning)
    for path in extra:
        print("note       : %s is not in the manifest (it will still be written)" % path)
    for problem in problems:
        print("integrity  : " + problem)
    if missing:
        print("\nMissing from the transcript (%d):" % len(missing))
        for path in missing:
            print("  - " + path)

    if missing and not args.allow_partial:
        print("\nNothing was written. Add the missing replies to the transcript and run again "
              "(or use --allow-partial).", file=sys.stderr)
        return 2
    if problems and args.strict:
        print("\nIntegrity problems found and --strict is set; nothing was written.", file=sys.stderr)
        return 2
    if args.check_only:
        print("\nCheck only: nothing was written.")
        return 0

    out_dir = Path(args.output_dir)
    zip_path = Path(args.zip_path)
    if not args.force:
        if out_dir.exists() and any(out_dir.iterdir()):
            fail("%s already exists and is not empty (use --force to overwrite)" % out_dir)
        if zip_path.exists():
            fail("%s already exists (use --force to overwrite)" % zip_path)
    out_dir.mkdir(parents=True, exist_ok=True)
    sums = write_tree(out_dir, files)
    archived = write_zip(out_dir, zip_path)

    print("\nWrote %d files to %s/ (plus SHA256SUMS.txt)" % (len(sums), out_dir))
    print("Packaged %d files into %s" % (archived, zip_path))
    if problems:
        print("Review the integrity warnings above before building.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
