package io.github.sds100.keymapper.inputmethod.keyboard.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.Nonnull;

import io.github.sds100.keymapper.inputmethod.keyboard.Key;
import io.github.sds100.keymapper.inputmethod.latin.common.Constants;

/**
 * Transforms keyboard rows into a split keyboard layout.
 * Divides the keys in each row into left and right halves aligned to the screen edges,
 * leaving an open gap in the middle, while keeping the spacebar as a single wide key
 * that bridges the center gap on its row.
 */
public final class SplitLayoutHelper {
    private static final float SPLIT_SCALE = 0.72f;

    private SplitLayoutHelper() {
        // Utility class
    }

    public static void applySplitLayout(@Nonnull final KeyboardParams params) {
        if (params.mSortedKeys.isEmpty()) {
            return;
        }

        final int keyboardWidth = params.mOccupiedWidth;
        final int leftPadding = params.mLeftPadding;
        final int rightPadding = params.mRightPadding;
        final int usableWidth = keyboardWidth - leftPadding - rightPadding;
        final int rightEdge = keyboardWidth - rightPadding;

        if (usableWidth <= 0) {
            return;
        }

        // Group keys into rows by Y coordinate (within 4px tolerance)
        final Map<Integer, List<Key>> rows = new TreeMap<>();
        for (final Key key : params.mSortedKeys) {
            final int keyY = key.getY();
            Integer targetY = null;
            for (final Integer existingY : rows.keySet()) {
                if (Math.abs(existingY - keyY) <= 4) {
                    targetY = existingY;
                    break;
                }
            }
            if (targetY == null) {
                targetY = keyY;
                rows.put(targetY, new ArrayList<Key>());
            }
            rows.get(targetY).add(key);
        }

        final List<Key> allNewKeys = new ArrayList<>();

        for (final List<Key> rowKeys : rows.values()) {
            if (rowKeys.isEmpty()) {
                continue;
            }

            // Sort row keys from left to right
            Collections.sort(rowKeys, (a, b) -> Integer.compare(a.getX(), b.getX()));

            // Find space key if present
            Key spaceKey = null;
            int spaceIndex = -1;
            for (int i = 0; i < rowKeys.size(); i++) {
                final Key k = rowKeys.get(i);
                if (k.getCode() == Constants.CODE_SPACE
                        || k.getBackgroundType() == Key.BACKGROUND_TYPE_SPACEBAR) {
                    spaceKey = k;
                    spaceIndex = i;
                    break;
                }
            }

            final List<Key> leftKeys;
            final List<Key> rightKeys;

            if (spaceIndex != -1) {
                leftKeys = rowKeys.subList(0, spaceIndex);
                rightKeys = rowKeys.subList(spaceIndex + 1, rowKeys.size());
            } else {
                final int splitIndex = (rowKeys.size() + 1) / 2;
                leftKeys = rowKeys.subList(0, splitIndex);
                rightKeys = rowKeys.subList(splitIndex, rowKeys.size());
            }

            // Place left keys starting from leftPadding
            int curX = leftPadding;
            for (final Key origKey : leftKeys) {
                final int origWidth = origKey.getWidth() + origKey.getHorizontalGap();
                final int newWidth = Math.round(origWidth * SPLIT_SCALE);
                final Key newKey = new Key(origKey, curX, newWidth);
                allNewKeys.add(newKey);
                curX += newWidth;
            }
            final int leftBoundaryX = curX;

            // Compute right keys' widths and starting X
            int totalRightWidth = 0;
            final int[] rightWidths = new int[rightKeys.size()];
            for (int i = 0; i < rightKeys.size(); i++) {
                final Key origKey = rightKeys.get(i);
                final int origWidth = origKey.getWidth() + origKey.getHorizontalGap();
                rightWidths[i] = Math.round(origWidth * SPLIT_SCALE);
                totalRightWidth += rightWidths[i];
            }

            curX = rightEdge - totalRightWidth;
            final int rightBoundaryX = curX;

            for (int i = 0; i < rightKeys.size(); i++) {
                final Key origKey = rightKeys.get(i);
                int newWidth = rightWidths[i];
                if (i == rightKeys.size() - 1) {
                    newWidth = rightEdge - curX;
                }
                final Key newKey = new Key(origKey, curX, newWidth);
                allNewKeys.add(newKey);
                curX += newWidth;
            }

            // If this row has a spacebar, span it across the center gap between left and right keys
            if (spaceKey != null) {
                final int spaceX = leftBoundaryX;
                final int spaceWidth = rightBoundaryX - leftBoundaryX;
                if (spaceWidth > 0) {
                    final Key newSpaceKey = new Key(spaceKey, spaceX, spaceWidth);
                    allNewKeys.add(newSpaceKey);
                }
            }
        }

        // Replace keys in params
        params.clearKeys();
        for (final Key newKey : allNewKeys) {
            params.onAddKey(newKey);
        }
    }
}
