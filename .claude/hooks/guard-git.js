#!/usr/bin/env node
// PreToolUse (Bash): block `git commit` / `git push` while the target repo is on `main`,
// and any push that names `main`. Work goes on a feature branch + PR (AGENTS.md "Git").
// Exit 2 = block, stderr goes to Claude.
const { execFileSync } = require('child_process');

// A real git invocation: start of the command or after ; & | ( or a newline —
// not text that merely mentions it (a commit message, an echo).
const LEAD = String.raw`(?:^|[;&|(]\s*|\n\s*)`;
const GIT = new RegExp(LEAD + String.raw`git\s+(?:-C\s+("[^"]+"|'[^']+'|\S+)\s+)?(commit|push)\b`);
const PUSH_MAIN = new RegExp(LEAD + String.raw`git\s+(?:-C\s+\S+\s+)?push\b[^|;&\n]*[\s:]main\b`);
const CD = new RegExp(LEAD + String.raw`cd\s+("[^"]+"|'[^']+'|[^\s;&|]+)\s*&&[^\n]*?\bgit\s`);

const unquote = (s) => (s ? s.replace(/^["']|["']$/g, '') : s);

let input = '';
process.stdin.on('data', (c) => (input += c));
process.stdin.on('end', () => {
  let cmd = '';
  try {
    cmd = (JSON.parse(input).tool_input || {}).command || '';
  } catch {
    process.exit(0);
  }
  const m = GIT.exec(cmd);
  if (!m) process.exit(0);

  // Check the repo the command actually targets, not just the session's.
  const dir = unquote(m[1]) || unquote((CD.exec(cmd) || [])[1]) || process.cwd();
  let branch = '';
  try {
    branch = execFileSync('git', ['-C', dir, 'rev-parse', '--abbrev-ref', 'HEAD'], {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    }).trim();
  } catch {
    process.exit(0);
  }
  if (branch === 'main' || PUSH_MAIN.test(cmd)) {
    process.stderr.write(
      `Blocked by .claude/hooks/guard-git.js: no commits or pushes on/to \`main\` (${dir}). ` +
        'Create a feature branch (git switch -c <type>/<name>) and open a PR.\n'
    );
    process.exit(2);
  }
  process.exit(0);
});
