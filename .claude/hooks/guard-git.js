#!/usr/bin/env node
// PreToolUse (Bash): block `git commit` / `git push` while the current branch is `main`.
// Work goes on a feature branch + PR (CLAUDE.md "Git Rules"). Exit 2 = block, stderr goes to Claude.
const { execSync } = require('child_process');

let input = '';
process.stdin.on('data', (c) => (input += c));
process.stdin.on('end', () => {
  let cmd = '';
  try {
    cmd = (JSON.parse(input).tool_input || {}).command || '';
  } catch {
    process.exit(0);
  }
  // Only a real git invocation (start of the command or after ; & | && || or a subshell),
  // not text that merely mentions it (e.g. a commit message or echo).
  const GIT = /(?:^|[;&|(]\s*|\n\s*)git\s+(?:-C\s+\S+\s+)?(commit|push)\b/;
  if (!GIT.test(cmd)) process.exit(0);

  let branch = '';
  try {
    branch = execSync('git rev-parse --abbrev-ref HEAD', { encoding: 'utf8' }).trim();
  } catch {
    process.exit(0);
  }
  const pushesMain = /(?:^|[;&|(]\s*|\n\s*)git\s+push\b[^|;&\n]*[\s:](main)\b/.test(cmd);
  if (branch === 'main' || pushesMain) {
    process.stderr.write(
      'Blocked by .claude/hooks/guard-git.js: no commits or pushes on/to `main`. ' +
        'Create a feature branch (git switch -c <type>/<name>) and open a PR.\n'
    );
    process.exit(2);
  }
  process.exit(0);
});
