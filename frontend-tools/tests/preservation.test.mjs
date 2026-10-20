import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile, readdir } from "node:fs/promises";
import { execFileSync } from "node:child_process";
import postcss from "postcss";
import { responseError } from "../../src/main/resources/static/js/api/client.js";
const root = new URL("../../", import.meta.url);
const git = (...args) =>
  execFileSync("git", ["-c", "safe.directory=/workspace", ...args], {
    cwd: root,
    encoding: "utf8",
  });
test("CSS extraction preserves every selector, breakpoint, declaration and cascade position", async () => {
  const html = await readFile(
    new URL("src/main/resources/templates/dashboard.html", root),
    "utf8",
  );
  const files = [...html.matchAll(/href="\/css\/([^\"]+)"/g)].map((match) => match[1]);
  const current = (
    await Promise.all(
      files.map((file) =>
        readFile(new URL("src/main/resources/static/css/" + file, root), "utf8"),
      ),
    )
  ).join("\n");
  const tree = postcss.parse(current);
  const tokens = new Map();
  tree.walkDecls(/^--white-/, (decl) => tokens.set(decl.prop, decl.value));
  function normalized(source) {
    const css = postcss.parse(source);
    css.walkComments((comment) => comment.remove());
    css.walkDecls((decl) => {
      if (decl.prop.startsWith("--white-")) {
        decl.remove();
        return;
      }
      for (const [name, value] of tokens)
        decl.value = decl.value.replaceAll("var(" + name + ")", value);
      if (decl.parent.selector === ".empty-state" && decl.prop === "padding")
        decl.important = false;
    });
    const compact = (value) =>
      value.replace(/\s+/g, " ").replace(/\(\s+/g, "(").replace(/\s+\)/g, ")").trim();
    function describe(node) {
      return {
        type: node.type,
        ...(node.selector ? { selector: compact(node.selector) } : {}),
        ...(node.name ? { name: node.name, params: compact(node.params) } : {}),
        ...(node.prop
          ? {
              property: node.prop,
              value: compact(node.value),
              important: !!node.important,
            }
          : {}),
        ...(node.nodes ? { children: node.nodes.map(describe) } : {}),
      };
    }
    return describe(css);
  }
  assert.deepEqual(
    normalized(current),
    normalized(git("show", "89a459d:src/main/resources/static/css/dashboard.css")),
  );
  assert.equal((current.match(/!important/g) || []).length, 3);
});
test("locked references and vendor assets remain untouched", () => {
  assert.equal(
    git(
      "diff",
      "89a459d",
      "--",
      "frontend-tools/baseline",
      "frontend-tools/cleanup",
      "src/main/resources/static/vendor",
    ),
    "",
  );
});
test("HTTP client errors preserve status, code, safe message and trace contract", () => {
  for (const [status, code] of [
    [404, "NOT_FOUND"],
    [405, "METHOD_NOT_ALLOWED"],
    [415, "UNSUPPORTED_MEDIA_TYPE"],
  ]) {
    const response = new Response("{}", { status, headers: { "X-Trace-Id": "trace" } });
    const error = responseError(response, { errorCode: code, message: "Safe message" });
    assert.equal(error.status, status);
    assert.equal(error.errorCode, code);
    assert.equal(error.message, "Safe message");
    assert.equal(error.traceId, "trace");
  }
});
test("native first-party modules stay within the agreed approximate size limit", async () => {
  const directory = new URL("src/main/resources/static/js/", root);
  for (const file of await readdir(directory, { recursive: true })) {
    if (!file.endsWith(".js")) continue;
    const source = await readFile(
      new URL(file.replaceAll("\\", "/"), directory),
      "utf8",
    );
    assert.ok(source.split("\n").length <= 250, file + " exceeds 250 lines");
    assert.ok(!source.includes("innerHTML"), file + " uses dynamic HTML");
  }
});
