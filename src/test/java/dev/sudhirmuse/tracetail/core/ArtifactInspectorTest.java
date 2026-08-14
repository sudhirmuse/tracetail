/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArtifactInspectorTest {
    @TempDir Path directory;
    @Test void inspectsAndDecompilesSelectedClass()throws Exception{Path source=directory.resolve("Hello.java");Files.writeString(source,"public class Hello { public String greet() { return \"hello\"; } }");int result=ToolProvider.getSystemJavaCompiler().run(null,null,null,"-d",directory.toString(),source.toString());assertTrue(result==0);Path jar=directory.resolve("hello.jar");try(JarOutputStream output=new JarOutputStream(Files.newOutputStream(jar))){output.putNextEntry(new JarEntry("Hello.class"));output.write(Files.readAllBytes(directory.resolve("Hello.class")));output.closeEntry();}ArtifactInspector inspector=new ArtifactInspector();assertTrue(inspector.inspect(jar).contains("Hello.class"));assertTrue(inspector.decompile(jar,"Hello.class").contains("greet"));}
}
