package io.github.andrealtb.lockscreenlyrics.diagnostics;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Read-only artwork descriptions used by the default-off media diagnostics. */
public final class ArtworkDiagnostics {
    private ArtworkDiagnostics() {
    }

    public static String describeMetadata(MediaMetadata metadata) {
        if (metadata == null) return "metadata=null";
        return "display=" + describeBitmap(safeBitmap(metadata, MediaMetadata.METADATA_KEY_DISPLAY_ICON))
                + " | art=" + describeBitmap(safeBitmap(metadata, MediaMetadata.METADATA_KEY_ART))
                + " | album=" + describeBitmap(safeBitmap(metadata, MediaMetadata.METADATA_KEY_ALBUM_ART))
                + " | displayUri=" + safeString(metadata, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
                + " | artUri=" + safeString(metadata, MediaMetadata.METADATA_KEY_ART_URI)
                + " | albumUri=" + safeString(metadata, MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                + " | lyricInfo=" + length(metadata.getString("lyricInfo"));
    }

    public static String describeSeedling(Object data) {
        if (data == null) return "seedling=null";
        return "package=" + invoke(data, "getPackageName")
                + " | song=" + invoke(data, "getSong")
                + " | artist=" + invoke(data, "getArtist")
                + " | artworkIcon=" + describeIcon(invokeObject(data, "getArtworkIcon"))
                + " | artworkUri=" + invoke(data, "getArtworkUri")
                + " | artworkColor=" + invoke(data, "getArtworkColor")
                + " | artworkBgColor=" + invoke(data, "getArtworkBgColor");
    }

    public static String describeLoaderResult(Object result) {
        if (result == null) return "result=null";
        Object first = invokeObject(result, "component1");
        Object second = invokeObject(result, "component2");
        Object third = invokeObject(result, "component3");
        return "uri=" + stringValue(first)
                + " | bitmap=" + (second instanceof Bitmap
                ? describeBitmap((Bitmap) second) : stringValue(second))
                + " | changed=" + stringValue(third)
                + " | type=" + result.getClass().getName();
    }

    public static String describeSeedlingBundle(Bundle bundle) {
        if (bundle == null) return "bundle=null";
        return "package=" + bundle.getString("packageName", "")
                + " | song=" + bundle.getCharSequence("songName", "")
                + " | artist=" + bundle.getCharSequence("artist", "")
                + " | artworkUri=" + bundle.getParcelable("artworkUri")
                + " | artworkBgColor=" + bundle.getString("artworkBackgroundUri", "")
                + " | artworkChanged=" + bundle.getBoolean("artworkUriContentChanged", false);
    }

    public static boolean isLikelyArtworkView(ImageView view) {
        if (view == null) return false;
        String name = resourceName(view).toLowerCase(Locale.ROOT);
        boolean named = name.contains("album") || name.contains("artwork")
                || name.contains("cover") || name.contains("media_art");
        return named || Math.max(view.getWidth(), view.getHeight()) >= 200;
    }

    public static String describeImageView(ImageView view) {
        if (view == null) return "view=null";
        return "view=" + view.getClass().getName()
                + " | id=" + resourceName(view)
                + " | size=" + view.getWidth() + 'x' + view.getHeight()
                + " | scaleType=" + view.getScaleType()
                + " | drawable=" + describeDrawable(view.getDrawable())
                + " | background=" + describeDrawable(view.getBackground());
    }

    public static String describeIcon(Object value) {
        if (!(value instanceof Icon)) return value == null ? "null" : value.getClass().getName();
        Icon icon = (Icon) value;
        int type = icon.getType();
        StringBuilder builder = new StringBuilder("type=").append(type);
        try {
            if (type == Icon.TYPE_BITMAP || type == Icon.TYPE_ADAPTIVE_BITMAP) {
                Object bitmap = invokeObject(icon, "getBitmap");
                builder.append(':').append(bitmap instanceof Bitmap
                        ? describeBitmap((Bitmap) bitmap) : stringValue(bitmap));
            } else if (type == Icon.TYPE_URI || type == Icon.TYPE_URI_ADAPTIVE_BITMAP) {
                builder.append(":uri=").append(icon.getUri());
            } else if (type == Icon.TYPE_RESOURCE) {
                builder.append(":res=").append(icon.getResPackage()).append('/').append(icon.getResId());
            }
        } catch (Throwable error) {
            builder.append(":unreadable-").append(error.getClass().getSimpleName());
        }
        return builder.toString();
    }

    public static String describeDrawable(Drawable drawable) {
        if (drawable == null) return "null";
        if (drawable instanceof BitmapDrawable) {
            return "BitmapDrawable:" + describeBitmap(((BitmapDrawable) drawable).getBitmap());
        }
        if (drawable instanceof ColorDrawable) {
            return "ColorDrawable:" + colorHex(((ColorDrawable) drawable).getColor());
        }
        return drawable.getClass().getName()
                + ":intrinsic=" + drawable.getIntrinsicWidth() + 'x' + drawable.getIntrinsicHeight();
    }

    public static String describeBitmap(Bitmap bitmap) {
        if (bitmap == null) return "null";
        if (bitmap.isRecycled()) return "recycled";
        int allocation = -1;
        try {
            allocation = bitmap.getAllocationByteCount();
        } catch (Throwable ignored) {
        }
        return bitmap.getWidth() + "x" + bitmap.getHeight()
                + ':' + (bitmap.getConfig() == null ? "unknown" : bitmap.getConfig().name())
                + ":alpha=" + bitmap.hasAlpha()
                + ":bytes=" + allocation
                + ":generation=" + bitmap.getGenerationId()
                + ":identity=" + System.identityHashCode(bitmap)
                + ":sample=" + sampleBitmap(bitmap);
    }

    private static String sampleBitmap(Bitmap bitmap) {
        if (bitmap.getConfig() == Bitmap.Config.HARDWARE) return "unavailable-hardware";
        try {
            int[] xs = {0, bitmap.getWidth() / 2, bitmap.getWidth() - 1};
            int[] ys = {0, bitmap.getHeight() / 2, bitmap.getHeight() - 1};
            int first = bitmap.getPixel(xs[0], ys[0]);
            int maxDelta = 0;
            Set<Integer> unique = new HashSet<>();
            for (int y : ys) {
                for (int x : xs) {
                    int color = bitmap.getPixel(x, y);
                    unique.add(color);
                    maxDelta = Math.max(maxDelta, colorDistance(first, color));
                }
            }
            String classification = maxDelta == 0 ? "solid" : maxDelta <= 12 ? "near-solid" : "varied";
            return classification + ":unique=" + unique.size()
                    + ":maxDelta=" + maxDelta + ":first=" + colorHex(first);
        } catch (Throwable error) {
            return "unavailable-" + error.getClass().getSimpleName();
        }
    }

    private static int colorDistance(int left, int right) {
        return Math.max(
                Math.max(Math.abs((left >>> 24) - (right >>> 24)),
                        Math.abs(((left >>> 16) & 0xff) - ((right >>> 16) & 0xff))),
                Math.max(Math.abs(((left >>> 8) & 0xff) - ((right >>> 8) & 0xff)),
                        Math.abs((left & 0xff) - (right & 0xff))));
    }

    private static Bitmap safeBitmap(MediaMetadata metadata, String key) {
        try {
            return metadata.getBitmap(key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String safeString(MediaMetadata metadata, String key) {
        String value = metadata.getString(key);
        if (value == null) return "";
        return value.length() <= 240 ? value : value.substring(0, 240);
    }

    private static String invoke(Object owner, String method) {
        return stringValue(invokeObject(owner, method));
    }

    private static Object invokeObject(Object owner, String methodName) {
        if (owner == null) return null;
        try {
            Method method = owner.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(owner);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String resourceName(View view) {
        if (view.getId() == View.NO_ID) return "none";
        try {
            return view.getResources().getResourceName(view.getId());
        } catch (Throwable ignored) {
            return Integer.toString(view.getId());
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }

    private static String colorHex(int color) {
        return String.format(Locale.ROOT, "%08X", color);
    }
}
