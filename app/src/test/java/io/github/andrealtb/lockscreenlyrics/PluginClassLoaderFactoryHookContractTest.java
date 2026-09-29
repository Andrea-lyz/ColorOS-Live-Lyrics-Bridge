package io.github.andrealtb.lockscreenlyrics;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public final class PluginClassLoaderFactoryHookContractTest {
    @Test
    public void coloros17FactoryStartsThePluginHooksOnceTheLoaderIsReady() throws Exception {
        String module = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/LockscreenLyricsModule.java");

        assertTrue(module.contains(
                "\"com.android.systemui.shared.plugins.PluginInstance$PluginFactory\""));
        assertTrue(module.contains("tryInstallPluginClassLoaderFactoryHook(classLoader)"));
        assertTrue(module.contains(".intercept(this::onPluginClassLoaderCreated)"));
        assertTrue(module.contains("OplusPluginClassLoaderPolicy.isReady(pluginLoader)"));
        // Both entries share one handler; the KuWo and controller hooks are never attempted twice.
        assertTrue(module.contains("readyPluginClassLoader.get() == pluginLoader"));
    }

    @Test
    public void coloros17EvidenceShowsTheLoaderHasNoConstructor() throws Exception {
        String evidence = readProjectFile(
                "app/src/test/resources/fixtures/systemui-plugin-classloader-evidence-c17.txt");

        assertTrue(evidence.contains("apkVersionName=17.99.02"));
        assertTrue(evidence.contains("loaderDirectMethods=<clinit>()V only; no <init>"));
        assertTrue(evidence.contains(
                "PluginInstance$PluginFactory;->createClassLoader()Ljava/lang/ClassLoader;"));
    }

    private static String readProjectFile(String relativePath) throws Exception {
        File direct = new File(relativePath);
        File file = direct.isFile()
                ? direct
                : new File(".." + File.separator + relativePath);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
