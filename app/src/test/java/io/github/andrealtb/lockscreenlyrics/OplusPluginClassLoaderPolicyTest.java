package io.github.andrealtb.lockscreenlyrics;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class OplusPluginClassLoaderPolicyTest {
    @Test
    public void coloros17LoaderIsNotReadyUntilItsPackageFilterIsAssigned() {
        FakeOplusLoader loader = new FakeOplusLoader();

        assertFalse(OplusPluginClassLoaderPolicy.isReady(loader));

        loader.mPackages = new String[]{"com.android.systemui.plugin"};
        assertTrue(OplusPluginClassLoaderPolicy.isReady(loader));
    }

    @Test
    public void loaderWithoutTheKnownFieldKeepsHistoricalReadiness() {
        assertTrue(OplusPluginClassLoaderPolicy.isReady(new UnknownLayoutLoader()));
        assertFalse(OplusPluginClassLoaderPolicy.isReady(null));
    }

    @Test
    public void pluginLoaderIsMatchedThroughItsClassHierarchy() {
        String name = FakeOplusLoader.class.getName();

        assertTrue(OplusPluginClassLoaderPolicy.isPluginClassLoader(new FakeOplusLoader(), name));
        assertTrue(OplusPluginClassLoaderPolicy.isPluginClassLoader(new FakeOplusLoaderChild(), name));
        assertFalse(OplusPluginClassLoaderPolicy.isPluginClassLoader(new UnknownLayoutLoader(), name));
        assertFalse(OplusPluginClassLoaderPolicy.isPluginClassLoader(null, name));
    }

    @Test
    public void factoryShapeIsAnInstanceClassLoaderSupplier() throws Exception {
        assertTrue(OplusPluginClassLoaderPolicy.isClassLoaderFactory(
                FakeFactory.class.getDeclaredMethod("createClassLoader")));
        assertFalse(OplusPluginClassLoaderPolicy.isClassLoaderFactory(
                FakeFactory.class.getDeclaredMethod("staticFactory")));
        assertFalse(OplusPluginClassLoaderPolicy.isClassLoaderFactory(
                FakeFactory.class.getDeclaredMethod("withPackage", String.class)));
        assertFalse(OplusPluginClassLoaderPolicy.isClassLoaderFactory(
                FakeFactory.class.getDeclaredMethod("notALoader")));
        assertFalse(OplusPluginClassLoaderPolicy.isClassLoaderFactory(null));
    }

    private static class FakeOplusLoader extends ClassLoader {
        String[] mPackages;
    }

    private static final class FakeOplusLoaderChild extends FakeOplusLoader {
    }

    private static final class UnknownLayoutLoader extends ClassLoader {
    }

    @SuppressWarnings("unused")
    private static final class FakeFactory {
        ClassLoader createClassLoader() {
            return null;
        }

        static ClassLoader staticFactory() {
            return null;
        }

        ClassLoader withPackage(String packageName) {
            return null;
        }

        Object notALoader() {
            return null;
        }
    }
}
