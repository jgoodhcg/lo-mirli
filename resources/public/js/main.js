// When plain htmx isn't quite enough, you can stick some custom JS here.

function setURLParameter(paramName, value) {
  const url = new URL(window.location);
  url.searchParams.set(paramName, value.toString());
  window.history.pushState({}, null, url.toString());
}
