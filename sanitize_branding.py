import os
import shutil

REPLACEMENTS = {
    "com.bny.bts": "com.enterprise.bts",
    "bny-jira-lite": "enterprise-jira-lite",
    "BNY Jira Lite": "Enterprise Jira Lite",
    "BNY Enterprise UI/UX": "Enterprise UI/UX",
    "@bny-demo.test": "@enterprise-demo.test",
    "BNY#Demo2026!": "Enterprise#Demo2026!",
    "com/bny/bts": "com/enterprise/bts",
}

EXTENSIONS = (".java", ".ts", ".tsx", ".xml", ".sql", ".yml", ".json", ".md", ".env", ".example")

# 1. Replace corporate branding across all source files
print("Replacing branding in files...")
for root, dirs, files in os.walk("."):
    # Skip build output directories
    if any(skip in root for skip in ("target", "node_modules", ".git", "dist")):
        continue
    for file in files:
        if file.endswith(EXTENSIONS):
            path = os.path.join(root, file)
            try:
                with open(path, "r", encoding="utf-8") as f:
                    content = f.read()

                new_content = content
                for old, new in REPLACEMENTS.items():
                    new_content = new_content.replace(old, new)

                if new_content != content:
                    with open(path, "w", encoding="utf-8") as f:
                        f.write(new_content)
                    print(f"Sanitized: {path}")
            except Exception as e:
                print(f"Skipped {path}: {e}")

# 2. Rename Java package folders from 'bny' to 'enterprise'
print("\nUpdating Java package directory structure...")
for base in [
    os.path.join("backend", "src", "main", "java", "com"),
    os.path.join("backend", "src", "test", "java", "com"),
]:
    old_dir = os.path.join(base, "bny")
    new_dir = os.path.join(base, "enterprise")
    if os.path.exists(old_dir):
        if os.path.exists(new_dir):
            shutil.rmtree(new_dir)
        os.rename(old_dir, new_dir)
        print(f"Renamed: {old_dir} -> {new_dir}")

print("\nDone! All corporate references neutralized safely.")