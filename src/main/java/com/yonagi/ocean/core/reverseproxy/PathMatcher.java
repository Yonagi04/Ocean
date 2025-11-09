package com.yonagi.ocean.core.reverseproxy;

import java.util.Arrays;
import java.util.Objects;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/09 10:18
 */
public class PathMatcher {

    private static final String PATH_SEPARATOR = "/";
    private static final String DOUBLE_WILD_CARD = "**";
    private static final String SINGLE_WILD_CARD = "*";

    public static boolean match(String pattern, String path) {
        if (pattern == null || path == null) {
            return false;
        }
        String normalizedPattern = normalizePath(pattern);
        String normalizedPath = normalizePath(path);
        if (Objects.equals(normalizedPattern, normalizedPath)) {
            return true;
        }

        String[] patternSegments = normalizedPattern.split(PATH_SEPARATOR);
        String[] pathSegments = normalizedPath.split(PATH_SEPARATOR);
        return doMatch(patternSegments, pathSegments);
    }

    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return PATH_SEPARATOR;
        }
        path = path.replaceAll("/+", PATH_SEPARATOR);

        if (!path.startsWith(PATH_SEPARATOR)) {
            path = PATH_SEPARATOR + path;
        }

        if (path.length() > 1 && path.endsWith(PATH_SEPARATOR)) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static boolean doMatch(String[] patternSegments, String[] pathSegments) {
        int patIdx = 0;
        int pathIdx = 0;

        while (patIdx < patternSegments.length && pathIdx < pathSegments.length) {
            String patternSegment = patternSegments[patIdx];
            String pathSegment = pathSegments[pathIdx];

            if (DOUBLE_WILD_CARD.equals(patternSegment)) {
                if (patIdx == patternSegments.length - 1) {
                    return true;
                }
                for (int i = pathIdx; i < pathSegments.length; i++) {
                    if (doMatch(
                            Arrays.copyOfRange(patternSegments, patIdx + 1, patternSegments.length),
                            Arrays.copyOfRange(pathSegments, i, pathSegments.length))) {
                        return true;
                    }
                }
                return false;
            } else if (isSingleWildCardMatch(patternSegment, pathSegment)) {
                patIdx++;
                pathIdx++;
            } else {
                return false;
            }
        }

        if (patIdx == patternSegments.length && pathIdx == pathSegments.length) {
            return true;
        }

        while (patIdx < patternSegments.length) {
            if (DOUBLE_WILD_CARD.equals(patternSegments[patIdx])) {
                patIdx++;
            } else {
                break;
            }
        }

        return patIdx == patternSegments.length && pathIdx == pathSegments.length;
    }

    private static boolean isSingleWildCardMatch(String pattern, String path) {
        if (!pattern.contains(SINGLE_WILD_CARD)) {
            return pattern.equals(path);
        }

        int patternIndex = 0;
        int pathIndex = 0;

        while (patternIndex < pattern.length() || pathIndex < path.length()) {
            if (patternIndex < pattern.length() && pattern.charAt(patternIndex) == SINGLE_WILD_CARD.charAt(0)) {
                int nextPatternIndex = patternIndex + 1;
                if (nextPatternIndex == pattern.length()) {
                    return true;
                }
                char nextPatternChar = pattern.charAt(nextPatternIndex);
                while (pathIndex < path.length() && path.charAt(pathIndex) != nextPatternChar) {
                    pathIndex++;
                }
                patternIndex = nextPatternIndex;
            } else if (patternIndex < pattern.length() && pathIndex < path.length() && pattern.charAt(patternIndex) == path.charAt(pathIndex)) {
                patternIndex++;
                pathIndex++;
            } else {
                return false;
            }
        }

        return patternIndex == pattern.length() && pathIndex == path.length();
    }
}
