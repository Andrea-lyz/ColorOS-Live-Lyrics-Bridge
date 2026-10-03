package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;

import io.github.andrealtb.artwork.contract.ArtworkContract;
import io.github.andrealtb.artwork.contract.ArtworkSigningIdentity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Explicit selection, exact current signer set and declared major version. Never loads plugin DEX. */
public final class ArtworkProviderDirectory {
    public record Provider(ComponentName component, String signingIdentity, int uid,
            String label, ComponentName settingsActivity) {}

    private ArtworkProviderDirectory() {}

    public static List<Provider> discover(Context context) {
        List<Provider> providers = new ArrayList<>();
        for (ResolveInfo result : context.getPackageManager().queryIntentServices(
                new Intent(ArtworkContract.ACTION_BIND), PackageManager.GET_META_DATA)) {
            if (result.serviceInfo == null) continue;
            try {
                ServiceInfo info = result.serviceInfo;
                providers.add(read(context, new ComponentName(info.packageName, info.name)));
            } catch (Exception ignored) {
                // One malformed/inaccessible provider cannot break discovery of another.
            }
        }
        providers.sort(Comparator.comparing(provider -> provider.component().flattenToString()));
        return providers;
    }

    public static Provider read(Context context, ComponentName component) throws Exception {
        PackageManager manager = context.getPackageManager();
        ServiceInfo info = manager.getServiceInfo(component, PackageManager.GET_META_DATA);
        if (!info.exported || !info.enabled || !info.applicationInfo.enabled
                || info.metaData == null
                || info.metaData.getInt(ArtworkContract.META_PROTOCOL_MAJOR, -1) != ArtworkContract.MAJOR
                || (info.permission != null && context.checkSelfPermission(info.permission)
                    != PackageManager.PERMISSION_GRANTED)) {
            throw new SecurityException("provider_unavailable");
        }
        ComponentName settings = null;
        String settingsName = info.metaData.getString(ArtworkContract.META_SETTINGS_ACTIVITY, "");
        if (!settingsName.isEmpty()) {
            if (settingsName.startsWith(".")) settingsName = info.packageName + settingsName;
            ComponentName candidate = new ComponentName(info.packageName, settingsName);
            try {
                ActivityInfo activity = manager.getActivityInfo(candidate, 0);
                if (activity.exported && activity.enabled && activity.applicationInfo.enabled
                        && (activity.permission == null || context.checkSelfPermission(activity.permission)
                            == PackageManager.PERMISSION_GRANTED)) settings = candidate;
            } catch (PackageManager.NameNotFoundException ignored) { /* optional settings */ }
        }
        return new Provider(component, ArtworkSigningIdentity.read(manager, info.packageName),
                info.applicationInfo.uid, info.loadLabel(manager).toString(), settings);
    }

    public static Provider verify(Context context, Provider selected) throws Exception {
        Provider installed = read(context, selected.component());
        if (!installed.signingIdentity().equals(selected.signingIdentity()) || installed.uid() != selected.uid()) {
            throw new SecurityException("provider_identity_changed");
        }
        return installed;
    }
}
