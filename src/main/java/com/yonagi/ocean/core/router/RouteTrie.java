package com.yonagi.ocean.core.router;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/24 16:37
 */
public class RouteTrie {

    private final Node root = new Node();

    private final ReentrantReadWriteLock rwlock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = rwlock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = rwlock.writeLock();

    private static class Node {
        // 静态路径段子节点
        final Map<String, Node> children = new ConcurrentHashMap<>();

        // 路径变量子节点 (如 {id})
        volatile Node pathVariableNode;
        String pathVariableName;

        // 通配符子节点 (如 *)
        volatile Node wildcardNode;

        // 对应的 RouteEntry (仅叶子节点有值)
        volatile Router.RouteEntry entry;

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Node other = (Node) obj;
            return Objects.equals(children, other.children) &&
                   Objects.equals(pathVariableNode, other.pathVariableNode) &&
                   Objects.equals(wildcardNode, other.wildcardNode) &&
                   Objects.equals(entry, other.entry);
        }

        @Override
        public int hashCode() {
            return Objects.hash(children, pathVariableNode, wildcardNode, entry);
        }
    }

    public void insert(Router.RouteEntry entry) {
        List<String> segments = entry.pathSegments;
        Node node = root;

        for (String segment : segments) {
            if (segment.startsWith("{") && segment.endsWith("}")) {
                if (node.pathVariableNode == null) {
                    writeLock.lock();
                    try {
                        if (node.pathVariableNode == null) {
                            Node newNode = new Node();
                            newNode.pathVariableName = segment.substring(1, segment.length() - 1);
                            node.pathVariableNode = newNode;
                        }
                    } finally {
                        writeLock.unlock();
                    }
                }
            } else if ("*".equals(segment)) {
                if (node.wildcardNode == null) {
                    writeLock.lock();
                    try {
                        if (node.wildcardNode == null) {
                            node.wildcardNode = new Node();
                        }
                    } finally {
                        writeLock.unlock();
                    }
                }
                node = node.wildcardNode;
                break;
            } else {
                node = node.children.computeIfAbsent(segment, k -> new Node());
            }
        }
        node.entry = entry;
    }

    public void remove(Router.RouteEntry entry) {
        Node node = root;
        for (String segment : entry.pathSegments) {
            if (segment.startsWith("{") && segment.endsWith("}")) {
                if (node.pathVariableNode == null) {
                    return;
                }
                node = node.pathVariableNode;
            } else if ("*".equals(segment)) {
                if (node.wildcardNode == null) {
                    return;
                }
                node = node.wildcardNode;
                break;
            } else {
                node = node.children.get(segment);
                if (node == null) {
                    return;
                }
            }
        }
        node.entry = null;
    }

    public void clear() {
        root.children.clear();
        root.pathVariableNode = null;
        root.wildcardNode = null;
        root.entry = null;
    }

    public static class TrieMatch {
        public final Router.RouteEntry entry;
        public final Map<String, String> pathVariables;

        public TrieMatch(Router.RouteEntry entry, Map<String, String> pathVariables) {
            this.entry = entry;
            this.pathVariables = pathVariables;
        }
    }

    public TrieMatch search(String path) {
        List<String> segments = Router.RouteEntry.parsePathSegments(path);
        Node node = root;
        Map<String, String> variables = null;

        readLock.lock();
        try {
            for (String segment : segments) {
                Node child = node.children.get(segment);
                if (child != null) {
                    node = child;
                    continue;
                }
                if (node.pathVariableNode != null) {
                    if (variables == null) {
                        variables = new HashMap<>();
                    }
                    variables.put(node.pathVariableNode.pathVariableName, segment);
                    node = node.pathVariableNode;
                    continue;
                }
                if (node.wildcardNode != null) {
                    node = node.wildcardNode;
                    if (node.entry != null) {
                        return new TrieMatch(node.entry, variables);
                    }
                }
                return null;
            }
            if (node.entry != null) {
                return new TrieMatch(node.entry, variables == null ? Collections.emptyMap() : variables);
            }
            return null;
        } finally {
            readLock.unlock();
        }
    }

    public int count() {
        return countNodes(root, new HashSet<>());
    }

    private int countNodes(Node node, Set<Node> visited) {
        if (node == null || visited.contains(node)) {
            return 0;
        }
        visited.add(node);

        int count = 0;
        if (node.entry != null) {
            count = 1;
        }

        for (Node child : node.children.values()) {
            count += countNodes(child, visited);
        }
        if (node.pathVariableNode != null) {
            count += countNodes(node.pathVariableNode, visited);
        }
        if (node.wildcardNode != null) {
            count += countNodes(node.wildcardNode, visited);
        }
        return count;
    }
}
