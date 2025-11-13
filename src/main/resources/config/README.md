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
- `path`: The URL path for the route. Use `*` to match all paths. Use `{variable}` to match variable in uri.
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

## reverse_proxy.json

This file defines the reverse proxy configuration for the Ocean. Each entry specifies how incoming requests should be forwarded to backend services. The file is in JSON format.

### Example Structure

```json
[
  {
    "enabled": true,
    "id": "echo",
    "path": "echo",
    "stripPrefix": false,
    "timeout": 3000,
    "lbConfig": {
      "strategy": "WEIGHT_ROUND_ROBIN",
      "healthCheckMode": "DISABLED",
      "checkIntervalMs": 3000,
      "upstreams": [
        {
          "url": "http://localhost:8088",
          "weight": 1.0
        }
      ],
      "canaryUpstreams": [
        {
          "url": "http://localhost:8089",
          "weight": 1.0
        }
      ],
      "canaryPercent": 30
    },
    "addHeaders": {
      "testHeader": "token"
    }
  }
]
```
- `enabled`: A boolean indicating whether this reverse proxy configuration is active.
- `id`: A unique identifier for the reverse proxy configuration.
- `path`: The URL path prefix that this reverse proxy configuration applies to. Use `*` to match all paths
- `stripPrefix`: A boolean indicating whether to remove the path prefix when forwarding requests to the backend. If you use `*` to match paths, we recommend setting this to `true`.
- `timeout`: The timeout duration (in milliseconds) for requests to the backend.
- `lbConfig`: The load balancing configuration for the reverse proxy.
  - `strategy`: The load balancing strategy (e.g., NONE, ROUND_ROBIN, IP_HASH, RANDOM).
  - `healthCheckMode`: The health check mode for upstream servers (e.g., ACTIVE_CHECK, PASSIVE_CHECK, DISABLED).
  - `checkIntervalMs`: The interval (in milliseconds) between health checks.
  - `upstreams`: A list of upstream backend servers.
    - `url`: The URL of the upstream server.
    - `weight`: The weight of the upstream server for load balancing, between 0 and 1.
    - `canaryUpstreams`: A list of canary upstream backend servers for canary deployments.
    - `canaryPercent`: The percentage of traffic to route to canary upstreams (0-100).
- `addHeaders`: A map of additional headers to add to requests forwarded to the backend.

If you set `healthChechMode` to **ACTIVE_CHECK** or **PASSIVE_CHECK**, Ocean will set up a simple client to periodically check the health status of each upstream server.

**Upstream server MUST provide an API for Ocean to detect health status, you can customize the path of API in `server.properties`**

ACTIVE_CHECK will actively send requests to the upstream servers at the specified interval, while PASSIVE_CHECK will send requests while the health status of upstream servers is down or unknown.
If a server is found to be unhealthy, it will be temporarily removed from the load balancing rotation until it is deemed healthy again.