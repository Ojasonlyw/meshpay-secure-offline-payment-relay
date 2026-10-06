/** Owns format dates. */


export const dateTime = new Intl.DateTimeFormat("en-IN", {
    dateStyle: "medium",
    timeStyle: "short",
  });

export const chartDate = new Intl.DateTimeFormat("en-IN", {
    day: "numeric", month: "short", timeZone: "UTC",
  });

export const activityTime = new Intl.DateTimeFormat("en-IN", {
    day: "numeric", month: "short", hour: "numeric", minute: "2-digit",
  });
