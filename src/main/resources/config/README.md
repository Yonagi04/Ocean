This directory contains configuration files for the Ocean.

## route.json

This file defines the routing configuration for the Ocean. Each route specifies the HTTP method, path, handler type, and other relevant details. The file is in JSON format. Below is an example structure:

### Example Structure

```json
[
  {
    "method": "GET",
    "path": "/api/detail",
    "type": "HANDLER",
    "handler": "com.yonagi.ocean.handler.impl.GenericApiHandler",
    "contentType": "application/json",
    "enabled": true
  }
]
```
- `method`: The HTTP method for the route (e.g., GET, POST). Specifying "ALL" means the route applies to all methods.
- `path`: The URL path for the route. Use `*` to match all paths.
- `type`: The type of route. It can be "HANDLER" for custom handlers, "STATIC" for static file serving, or "REDIRECT" for URL redirection.
- `handler`: The fully qualified class name of the handler. This is required if the type is "HANDLER".
- `contentType`: The Content-Type of the response (e.g., application/json, text/html).
- `enabled`: A boolean indicating whether the route is active.
- `targetUrl`: The target URL for redirection. This is required if the type is "REDIRECT".
- `statusCode`: The HTTP status code for redirection (e.g., 301, 302). This is optional and defaults to 302 if not specified.

## ratelimit.json

This file defines the rate limiting rules for specific URI patterns. It links a request pattern (method + path) to one or more predefined rate limit policies (scopes). The file is in JSON format.

Note: The specific capacity and rate for each policy (IP_URI, GLOBAL_URI, etc.) are configured separately in the properties file (e.g., server.properties).

### Example Structure
```json
[
  {
    "method": "GET",
    "path": "/404.html",
    "scopes": [
      "IP_URI"
    ],
    "enabled": true
  },
  {
    "method": "ALL",
    "path": "/50x.html",
    "scopes": [
      "IP_GLOBAL"
    ],
    "enabled": true
  }
]
```
- `method`: The HTTP method for the rate limiting (e.g., GET, POST). Specifying "ALL" means the rate limiting applies to all methods.
- `path`: The URL path for rate limiting. Use `*` to match all paths.
- `scopes`: A list of rate limiting policies to apply to the matched request. A request must pass all applied policies.
- `enabled`: A boolean indicating whether this specific rate limit rule is active.

### Available Rate Limit Scopes

| Scope      | Description                                                  |
| ---------- | ------------------------------------------------------------ |
| GLOBAL_URI | Global path rate limiting: Limit the total number of visits to the URI by all IP addresses |
| IP_URI     | Client path rate limiting: Limits the number of independent visits to the URI by a single IP address. |
| IP_GLOBAL  | Client global rate limiting: Limits the total number of visits to all paths by a single IP address. |

