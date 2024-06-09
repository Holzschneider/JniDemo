package de.dualuse.jnidemo;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * This class is used to load a JNI library from the classpath. It will extract the library from the classpath to a temporary file and load it.
 * The library is extracted to a temporary file because the library cannot be loaded from the classpath directly.
 * The library is loaded using {@link System#load(String)}.
 * <p>
 * The library is assumed to be stored in the classpath in a directory structure that matches the OS and architecture of the system.
 * The directory structure follows the pattern provided by gradle's cpp-library plugin:
 * <pre>
 *    / &lt;OS&gt; / &lt;ARCH&gt; / &lt;libraryFileName&gt;
 * </pre>
 * <p>
 * The OS and ARCH are determined by the system properties {@code os.name} and {@code os.arch} respectively.
 */
public class JNILibrary {
    interface NameMapping { String apply(String name); }
    interface NameMappingFactory { NameMapping apply(String name); }
    static class Config<K,V> extends HashMap<K,V> { @SafeVarargs final Config<K,V> add(V v, K... ks) { for (K k:ks) put(k,v); return this; } }

    static String[] WINDOWS = {"windows","windows 7","windows 8","windows 8.1","windows 10","windows 11"};
    static String[] MAC_OS = {"mac os x", "darwin"};
    static String[] LINUX = {"linux"};

    static final NameMappingFactory LIBRARY_FILENAME_MAPPING = new Config<String,NameMapping>()
        .add(name -> name+".dll", WINDOWS)
        .add(name -> "lib"+name+".dylib", MAC_OS)
        .add(name -> "lib"+name+".so", LINUX)
        ::get;

    //https://stackoverflow.com/questions/10846105/all-possible-values-os-arch-in-32bit-jre-and-in-64bit-jre
    static final NameMapping ARCH_DIRECTORY_MAPPING = new Config<String,String>()
        .add("/x86/", "x86")
        .add("/x86-64/", "amd64", "x86_64")
        .add("/aarch64/", "arm64", "aarch64")
        ::get;

    static final NameMapping OS_DIRECTORY_MAPPING = new Config<String,String>()
        .add("/windows/", WINDOWS)
        .add("/macos/", MAC_OS)
        .add("/linux/", LINUX)
        ::get;

    final public String libraryBaseName;
    final public String libraryFileName;

    private JNILibrary(String libraryName) {
        this.libraryBaseName = libraryName;

        libraryFileName = LIBRARY_FILENAME_MAPPING.apply(OS).apply(libraryName);
        String prefix = libraryFileName.substring(0, libraryFileName.lastIndexOf('.'));
        String suffix = libraryFileName.substring(prefix.length());

        File archiveDir = new File(OS_DIRECTORY_MAPPING.apply(OS), ARCH_DIRECTORY_MAPPING.apply(ARCH));
        File archivePath = new File(archiveDir, libraryFileName);

        try (InputStream in = JNILibrary.class.getResourceAsStream(archivePath.getPath().replaceAll("\\\\", "/"))) {
            assert in != null;
            File output = File.createTempFile(prefix, suffix);
            Files.copy(in, output.toPath(), REPLACE_EXISTING);

            System.load(output.getAbsolutePath());
            output.deleteOnExit();
        } catch (Exception ex) {
            var usa = new UnsatisfiedLinkError("Could not load library '"+libraryName+"' from '"+archivePath /* +"' or '"+localSrc.getAbsolutePath()+"'"*/);
            usa.initCause(ex);
            throw usa;
        }
    }

    static final String ARCH = System.getProperty("os.arch").toLowerCase(Locale.US);
    static final String OS = System.getProperty("os.name").toLowerCase(Locale.US);

    private static final HashMap<String,JNILibrary> LIBRARY_CACHE = new HashMap<>();
    static public synchronized JNILibrary load(String libraryName) {
        return LIBRARY_CACHE.computeIfAbsent(libraryName, JNILibrary::new);
    }

}
