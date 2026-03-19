# bin/tailwindcss-v4

This directory contains a vendored Tailwind CSS v4 standalone binary (Mach-O arm64, ~76MB).

## git status killed issue

Because the binary is tracked directly in git (not via Git LFS), `git status` must
hash the entire 76MB file during its index refresh phase. On macOS, this can cause
`git status` to hang or be killed (exit code 137 / SIGKILL).

### Workaround

Tell git to skip stat-checking this file:

```sh
git update-index --assume-unchanged bin/tailwindcss-v4
```

### Updating the binary

If you need to update `tailwindcss-v4`, temporarily undo the flag first:

```sh
git update-index --no-assume-unchanged bin/tailwindcss-v4
git add bin/tailwindcss-v4
git commit -m "Update tailwindcss binary"
git update-index --assume-unchanged bin/tailwindcss-v4
```
