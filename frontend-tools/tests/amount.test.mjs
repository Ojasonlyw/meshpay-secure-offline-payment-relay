import { test } from "node:test";
import assert from "node:assert/strict";
import { isPaymentAmount } from "../../src/main/resources/static/js/api/validators.js";

test("demo amounts preserve cents and the maximum through JSON", () => {
  for (const value of ["0.01", "0.10", "12.34", "1000000", "1000000.00"]) {
    assert.equal(isPaymentAmount(value), true, value);
    const [whole, fraction = ""] = value.split(".");
    assert.equal(
      JSON.parse(JSON.stringify(Number(value))).toFixed(2),
      whole + "." + fraction.padEnd(2, "0"),
    );
  }
});

test("demo amounts reject excess precision, exponents and unsafe ranges", () => {
  for (const value of [
    "0",
    "0.00",
    "-1",
    "1.001",
    "1.000",
    "1e2",
    "1E-2",
    "1000000.01",
    "100000000000000.01",
    "NaN",
    "Infinity",
    "",
    " 1",
    "text",
  ])
    assert.equal(isPaymentAmount(value), false, value);
});
