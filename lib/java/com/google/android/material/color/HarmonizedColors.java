/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.material.color;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.TypedArray;
import android.content.res.loader.ResourcesLoader;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import java.util.HashMap;
import java.util.Map;

/** A harmonizer class for harmonizing color resources and attributes. */
public class HarmonizedColors {

  private HarmonizedColors() {}

  private static final String TAG = HarmonizedColors.class.getSimpleName();

  /** Harmonizes the specified color resources, attributes, and theme overlay. */
  @NonNull
  public static void applyIfAvailable(@NonNull HarmonizedColorsOptions options) {
    if (!isHarmonizedColorAvailable()) {
      return;
    }
    Context context = options.getContext();
    int themeOverlay = options.getThemeOverlayResourceId();

    if (addResourcesLoaderToContext(context, options) && themeOverlay != 0) {
      ThemeUtils.applyThemeOverlay(context, themeOverlay);
    }
  }

  /**
   * Wraps the given Context from HarmonizedColorsOptions with the color resources being harmonized.
   *
   * <p>If a theme overlay is not provided in HarmonizedColorAttributes, then the context provided
   * in HarmonizedColorsOptions will be returned.
   */
  @NonNull
  public static Context wrapContextIfAvailable(@NonNull HarmonizedColorsOptions options) {
    Context context = options.getContext();
    if (!isHarmonizedColorAvailable()) {
      return context;
    }
    int themeOverlay = options.getThemeOverlayResourceId();
    Context newContext =
        themeOverlay == 0
            ? new ContextWrapper(context)
            : new ContextThemeWrapper(context, themeOverlay);

    return addResourcesLoaderToContext(newContext, options) ? newContext : context;
  }

  /** Returns {@code true} if harmonized colors are available on the current SDK level. */
  @ChecksSdkIntAtLeast(api = VERSION_CODES.R)
  private static boolean isHarmonizedColorAvailable() {
    return VERSION.SDK_INT >= VERSION_CODES.R;
  }

  @RequiresApi(api = VERSION_CODES.R)
  private static boolean addResourcesLoaderToContext(
      Context context, HarmonizedColorsOptions options) {
    ResourcesLoader resourcesLoader =
        ColorResourcesLoaderCreator.create(context, createHarmonizedColorReplacementMap(options));
    if (resourcesLoader != null) {
      context.getResources().addLoaders(resourcesLoader);
      return true;
    }
    return false;
  }

  @RequiresApi(api = VERSION_CODES.LOLLIPOP)
  private static Map<Integer, Integer> createHarmonizedColorReplacementMap(
      HarmonizedColorsOptions options) {
    Context context = options.getContext();
    Map<Integer, Integer> colorReplacementMap = new HashMap<>();
    int colorToHarmonizeWith =
        MaterialColors.getColor(context, options.getColorAttributeToHarmonizeWith(), TAG);

    // Harmonize color resources.
    for (int colorResourceId : options.getColorResourceIds()) {
      int harmonizedColor =
          MaterialColors.harmonize(
              ContextCompat.getColor(context, colorResourceId), colorToHarmonizeWith);
      colorReplacementMap.put(colorResourceId, harmonizedColor);
    }

    HarmonizedColorAttributes colorAttributes = options.getColorAttributes();
    if (colorAttributes != null) {
      int[] attributes = colorAttributes.getAttributes();
      if (attributes.length > 0) {
        // Harmonize theme overlay attributes in the custom theme overlay. If custom theme overlay
        // is not provided, look up resources value the theme attributes point to and
        // harmonize directly.
        int themeOverlay = colorAttributes.getThemeOverlay();
        TypedArray themeAttributesTypedArray = context.obtainStyledAttributes(attributes);
        TypedArray themeOverlayAttributesTypedArray =
            themeOverlay != 0
                ? new ContextThemeWrapper(context, themeOverlay).obtainStyledAttributes(attributes)
                : null;
        addHarmonizedColorAttributesToReplacementMap(
            colorReplacementMap,
            themeAttributesTypedArray,
            themeOverlayAttributesTypedArray,
            colorToHarmonizeWith);

        themeAttributesTypedArray.recycle();
        if (themeOverlayAttributesTypedArray != null) {
          themeOverlayAttributesTypedArray.recycle();
        }
      }
    }
    return colorReplacementMap;
  }

  // TypedArray.getType() requires API >= 21.
  @RequiresApi(api = VERSION_CODES.LOLLIPOP)
  private static void addHarmonizedColorAttributesToReplacementMap(
      @NonNull Map<Integer, Integer> colorReplacementMap,
      @NonNull TypedArray themeAttributesTypedArray,
      @Nullable TypedArray themeOverlayAttributesTypedArray,
      @ColorInt int colorToHarmonizeWith) {
    TypedArray resourceIdTypedArray =
        themeOverlayAttributesTypedArray != null
            ? themeOverlayAttributesTypedArray
            : themeAttributesTypedArray;

    for (int i = 0; i < themeAttributesTypedArray.getIndexCount(); i++) {
      int resourceId = resourceIdTypedArray.getResourceId(i, 0);
      if (resourceId != 0
          && themeAttributesTypedArray.hasValue(i)
          && isColorResource(themeAttributesTypedArray.getType(i))) {
        int colorToHarmonize = themeAttributesTypedArray.getColor(i, 0);
        colorReplacementMap.put(
            resourceId, MaterialColors.harmonize(colorToHarmonize, colorToHarmonizeWith));
      }
    }
  }

  private static boolean isColorResource(int attrType) {
    return (TypedValue.TYPE_FIRST_COLOR_INT <= attrType)
        && (attrType <= TypedValue.TYPE_LAST_COLOR_INT);
  }
}
