// Contract tests for the read-only shell predicate. Read-only mode keeps the
// shell available for remote reading, so this predicate is the only thing that
// stops "read-only" from becoming an arbitrary command runner.

#include "gtest_support.h"

#include "application/tool_read_only_policy.h"

namespace {

using linecode::application::IsReadOnlyShellCommand;

// Plain reads, including the shell-only read path an SSH workspace needs.
void AllowsReadOnlyCommands() {
  EXPECT_EXPRESSION(IsReadOnlyShellCommand("pwd"));
  EXPECT_EXPRESSION(IsReadOnlyShellCommand("ls -la"));
  EXPECT_EXPRESSION(IsReadOnlyShellCommand("find . -name '*.cpp' -maxdepth 2"));
  EXPECT_EXPRESSION(IsReadOnlyShellCommand("cat src/main.cpp"));
  EXPECT_EXPRESSION(IsReadOnlyShellCommand("head -n 40 README.md"));
  EXPECT_EXPRESSION(IsReadOnlyShellCommand("tail -n 20 build.log"));
  EXPECT_EXPRESSION(IsReadOnlyShellCommand("grep -rn TODO src"));
  EXPECT_EXPRESSION(IsReadOnlyShellCommand("rg -n 'include' src"));
  EXPECT_EXPRESSION(IsReadOnlyShellCommand("git status"));
  EXPECT_EXPRESSION(IsReadOnlyShellCommand("git diff --stat"));
  EXPECT_EXPRESSION(IsReadOnlyShellCommand("git log -n 5"));
  EXPECT_EXPRESSION(IsReadOnlyShellCommand("git show HEAD"));
  EXPECT_EXPRESSION(IsReadOnlyShellCommand("wc -l src/app.cpp"));
  EXPECT_EXPRESSION(IsReadOnlyShellCommand("sed -n '1,20p' src/app.cpp"));
  EXPECT_EXPRESSION(IsReadOnlyShellCommand("  pwd  "));
}

// A pipeline is fine while every stage only reads.
void AllowsReadOnlyPipelines() {
  EXPECT_EXPRESSION(IsReadOnlyShellCommand("grep -rn TODO src | head -n 20"));
  EXPECT_EXPRESSION(
      IsReadOnlyShellCommand("find . -name '*.h' | sort | uniq"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("grep -rn TODO src | tee out.txt"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("cat notes.md | rm -rf build"));
}

// Anything that can change the target, or that cannot be proven safe, is
// refused -- including the in-place edit form of an otherwise read-only tool.
void RefusesAnythingThatCanWrite() {
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand(""));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("   "));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("rm -rf build"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("mv a b"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("cp a b"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("mkdir -p out"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("touch marker"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("chmod 777 build.sh"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("echo hi > file.txt"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("echo hi >> file.txt"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("cat < input.txt"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("echo `whoami`"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("echo $(whoami)"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("pwd && rm -rf build"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("pwd ; rm -rf build"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("sleep 1 &"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("sed -i 's/a/b/' src/app.cpp"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("sed --in-place 's/a/b/' f.txt"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("git commit -m x"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("git checkout ."));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("git clean -fd"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("git"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("python3 -c \"open('x','w')\""));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("apt-get install -y curl"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("npm install"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("make -j8"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("curl https://example.com -o out"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("ssh host uptime"));
  EXPECT_EXPRESSION(!IsReadOnlyShellCommand("pkill -f node"));
}

} // namespace

TEST(tool_read_only_policy_tests, AllowsReadOnlyCommands) {
  AllowsReadOnlyCommands();
}
TEST(tool_read_only_policy_tests, AllowsReadOnlyPipelines) {
  AllowsReadOnlyPipelines();
}
TEST(tool_read_only_policy_tests, RefusesAnythingThatCanWrite) {
  RefusesAnythingThatCanWrite();
}
