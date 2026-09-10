package io.github.andrealtb.lockscreenlyrics;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Public broadcast contract for Universal Player Provider host-package bindings.
 *
 * <p>Bridge never stores the Provider applicationId. Presence is proven by the identity extra on
 * the binding broadcast, and only the bound host player packages are admitted into the runtime
 * OPlus history union.</p>
 */
final class UniversalPlayerBridgeContract {
    static final String ACTION_PLAYER_BINDINGS_CHANGED =
            "io.github.andrealtb.universallyrics.action.PLAYER_BINDINGS_CHANGED";
    static final String EXTRA_BOUND_PACKAGES = "extra_bound_packages";
    static final String EXTRA_PROVIDER_IDENTITY = "extra_provider_identity";
    static final String PROVIDER_IDENTITY = "universal-player-provider";
    static final String PREF_PRESENT = "universal_player_provider_present";
    static final String PREF_BOUND_PACKAGES = "universal_player_bound_packages";
    static final String TRANSLATION_GROUP_PACKAGE = "__universal_player__";
    static final String HEYTAP_MUSIC = "com.heytap.music";

    private static final Set<String> EXTRA_HISTORY_PACKAGES = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger BINDINGS_GENERATION = new AtomicInteger();
    private static volatile boolean present;

    private UniversalPlayerBridgeContract() {
    }

    static boolean isAcceptedIdentity(String identity) {
        return PROVIDER_IDENTITY.equals(identity);
    }

    static boolean isBlockedFromUniversalAdmission(String packageName) {
        return PlayerSystemUiPolicy.QQ_MUSIC.equals(packageName)
                || PlayerSystemUiPolicy.NETEASE_MUSIC.equals(packageName)
                || PlayerSystemUiPolicy.NETEASE_HONOR.equals(packageName)
                || PlayerSystemUiPolicy.KUGOU.equals(packageName)
                || PlayerSystemUiPolicy.KUGOU_LITE.equals(packageName)
                || PlayerSystemUiPolicy.KUWO.equals(packageName)
                || HEYTAP_MUSIC.equals(packageName);
    }

    static List<String> sanitizeBoundPackages(Iterable<String> packages) {
        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        if (packages == null) {
            return Collections.emptyList();
        }
        for (String packageName : packages) {
            if (packageName == null) {
                continue;
            }
            String trimmed = packageName.trim();
            if (trimmed.isEmpty()
                    || TRANSLATION_GROUP_PACKAGE.equals(trimmed)
                    || isBlockedFromUniversalAdmission(trimmed)) {
                continue;
            }
            sanitized.add(trimmed);
        }
        return new ArrayList<>(sanitized);
    }

    static void applyRuntimeBindings(boolean providerPresent, Collection<String> packages) {
        EXTRA_HISTORY_PACKAGES.clear();
        if (providerPresent) {
            EXTRA_HISTORY_PACKAGES.addAll(sanitizeBoundPackages(packages));
        }
        present = providerPresent;
        BINDINGS_GENERATION.incrementAndGet();
    }

    static boolean isPresent() {
        return present;
    }

    static int bindingsGeneration() {
        return BINDINGS_GENERATION.get();
    }

    static Set<String> extraHistoryPackages() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(EXTRA_HISTORY_PACKAGES));
    }

    static boolean isExtraHistoryPackage(String packageName) {
        return packageName != null && EXTRA_HISTORY_PACKAGES.contains(packageName);
    }

    static void persist(
            SharedPreferences preferences,
            boolean providerPresent,
            Collection<String> packages) {
        if (preferences == null) {
            applyRuntimeBindings(providerPresent, packages);
            return;
        }
        List<String> sanitized = providerPresent
                ? sanitizeBoundPackages(packages)
                : Collections.emptyList();
        preferences.edit()
                .putBoolean(PREF_PRESENT, providerPresent)
                .putStringSet(PREF_BOUND_PACKAGES, new LinkedHashSet<>(sanitized))
                .apply();
        applyRuntimeBindings(providerPresent, sanitized);
    }

    static void restoreFrom(SharedPreferences preferences) {
        if (preferences == null) {
            applyRuntimeBindings(false, Collections.emptyList());
            return;
        }
        applyRuntimeBindings(
                preferences.getBoolean(PREF_PRESENT, false),
                preferences.getStringSet(PREF_BOUND_PACKAGES, Collections.emptySet()));
    }

    static boolean isPresent(SharedPreferences preferences) {
        return preferences != null && preferences.getBoolean(PREF_PRESENT, false);
    }

    static List<String> boundPackages(SharedPreferences preferences) {
        if (preferences == null) {
            return Collections.emptyList();
        }
        return sanitizeBoundPackages(
                preferences.getStringSet(PREF_BOUND_PACKAGES, Collections.emptySet()));
    }
}

