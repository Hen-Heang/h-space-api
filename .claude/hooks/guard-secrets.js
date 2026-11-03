#!/usr/bin/env node
// PreToolUse (Write|Edit): block content that looks like a real secret.
// Exit 2 = block, stderr goes to Claude. Example/template files may hold empty placeholders only.
let input = '';
process.stdin.on('data', (c) => (input += c));
process.stdin.on('end', () => {
  let ti = {};
  try {
    ti = JSON.parse(input).tool_input || {};
  } catch {
    process.exit(0);
  }
  const file = String(ti.file_path || '').replace(/\\/g, '/');
  const text = [ti.content, ti.new_string].filter(Boolean).join('\n');
  if (!text) process.exit(0);

  const patterns = [
    [/-----BEGIN [A-Z ]*PRIVATE KEY-----/, 'private key'],
    [/\beyJ[A-Za-z0-9_-]{15,}\.eyJ[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{20,}/, 'JWT (e.g. Supabase service-role key)'],
    [/\bgh[pousr]_[A-Za-z0-9]{30,}/, 'GitHub token'],
    [/\bAKIA[0-9A-Z]{16}\b/, 'AWS access key'],
    [/\bsk_(live|test)_[A-Za-z0-9]{16,}/, 'API secret key'],
    // Skips placeholders (user:password@host, <pw>, ${VAR}) and local hosts.
    [
      /\bpostgres(?:ql)?:\/\/[^:\s/]+:(?!(?:password|passwd|pass|secret|changeme)@)[^@\s<>${}]{6,}@(?!localhost|127\.0\.0\.1|postgres[:/]|host\b)/,
      'database URL with password',
    ],
  ];
  // Non-empty secret-ish assignments in env/properties files (yml uses ${VAR:...} so is skipped).
  const isEnvLike = /(^|\/)(\.env[^/]*|[^/]+\.env|[^/]+\.properties)$/.test(file);
  const isExample = /\.example$|example\.|\.sample$/.test(file);
  if (isEnvLike && !isExample) {
    patterns.push([/^\s*[A-Z0-9_]*(SECRET|PASSWORD|_KEY|TOKEN)[A-Z0-9_]*\s*=\s*\S{8,}/m, 'secret assignment']);
  }

  for (const [re, what] of patterns) {
    if (re.test(text)) {
      process.stderr.write(
        `Blocked by .claude/hooks/guard-secrets.js: content for ${file || 'file'} looks like a ${what}. ` +
          'Secrets belong in a git-ignored .env / secret store, never in files Claude writes. ' +
          'Use a placeholder or ${ENV_VAR} reference instead.\n'
      );
      process.exit(2);
    }
  }
  process.exit(0);
});
