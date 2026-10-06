/** Owns ui charts. */

/** chartPoint owns its existing dashboard behavior.
 * @param {*} points
 * @param {*} index
 * @param {*} key
 * @param {*} max
 * @returns {*}
 */
export function chartPoint(points, index, key, max) {
  return {
    x: 25 + (index * 669) / Math.max(1, points.length - 1),
    y: 224 - (Number(points[index][key] || 0) * 185) / max,
  };
}

/** linePath owns its existing dashboard behavior.
 * @param {*} points
 * @param {*} key
 * @param {*} max
 * @returns {*}
 */
export function linePath(points, key, max) {
  if (!points.length) return "";
  const coordinates = points.map((_, index) =>
    chartPoint(points, index, key, max),
  );
  if (coordinates.length === 1)
    return `M${coordinates[0].x.toFixed(1)} ${coordinates[0].y.toFixed(1)}`;
  const slopes = coordinates
    .slice(1)
    .map(
      (point, index) =>
        (point.y - coordinates[index].y) / (point.x - coordinates[index].x),
    );
  const tangents = coordinates.map((_, index) => {
    if (index === 0) return slopes[0];
    if (index === coordinates.length - 1) return slopes.at(-1);
    const before = slopes[index - 1];
    const after = slopes[index];
    return before * after <= 0 ? 0 : (2 * before * after) / (before + after);
  });
  return coordinates.slice(1).reduce(
    (path, point, index) => {
      const previous = coordinates[index];
      const third = (point.x - previous.x) / 3;
      return (
        `${path} C${(previous.x + third).toFixed(1)} ${(previous.y + tangents[index] * third).toFixed(1)} ` +
        `${(point.x - third).toFixed(1)} ${(point.y - tangents[index + 1] * third).toFixed(1)} ` +
        `${point.x.toFixed(1)} ${point.y.toFixed(1)}`
      );
    },
    `M${coordinates[0].x.toFixed(1)} ${coordinates[0].y.toFixed(1)}`,
  );
}
