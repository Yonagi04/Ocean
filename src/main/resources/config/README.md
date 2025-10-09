This directory contains configuration files for the Ocean.

## route.json

This file defines the routing configuration for the Ocean.
Each route specifies the HTTP method, path, handler type, and other relevant details.
The file is in JSON format. Below is an example structure:

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
- `method`: The HTTP method for the route (e.g., GET, POST).
- `path`: The URL path for the route.
- `type`: The type of route. It can be "HANDLER" for custom handlers, "STATIC" for static file serving, or "REDIRECT" for URL redirection.
- `handler`: The fully qualified class name of the handler. This is required if the type is "HANDLER".
- `contentType`: The Content-Type of the response (e.g., application/json, text/html).
- `enabled`: A boolean indicating whether the route is active.
- `targetUrl`: The target URL for redirection. This is required if the type is "REDIRECT".
- `statusCode`: The HTTP status code for redirection (e.g., 301, 302). This is optional and defaults to 302 if not specified.