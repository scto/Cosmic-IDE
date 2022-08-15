package org.cosmic.ide.android.task.jar;

import org.cosmic.ide.android.interfaces.*;
import org.cosmic.ide.common.util.FileUtil;
import org.cosmic.ide.project.JavaProject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class JarTask implements Task {

    @Override
    public void doFullTask(JavaProject project) throws Exception {

        // input file
        var classesFolder = new File(project.getBinDirPath() + "classes");

        // Open archive file
        var stream = new FileOutputStream(new File(project.getBinDirPath() + "classes.jar"));

        var manifest = buildManifest();

        // Create the jar file
        var out = new JarOutputStream(stream, manifest);

        // Add the files..
        if (classesFolder.listFiles() != null) {
            for (var clazz : classesFolder.listFiles()) {
                add(classesFolder.getPath(), clazz, out);
            }
        }

        out.close();
        stream.close();
    }

    @Override
    public String getTaskName() {
        return "JarTask";
    }

    private Manifest buildManifest() {
        var manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        return manifest;
    }

    private void add(String parentPath, File source, JarOutputStream target) throws IOException {
        var name = source.getPath().substring(parentPath.length() + 1);

        BufferedInputStream in = null;
        try {
            if (source.isDirectory()) {
                if (!name.isEmpty()) {
                    if (!name.endsWith("/")) name += "/";

                    // Add the Entry
                    var entry = new JarEntry(name);
                    entry.setTime(source.lastModified());
                    target.putNextEntry(entry);
                    target.closeEntry();
                }

                for (var nestedFile : source.listFiles()) {
                    add(parentPath, nestedFile, target);
                }
                return;
            }

            var entry = new JarEntry(name);
            entry.setTime(source.lastModified());
            target.putNextEntry(entry);
            in = new BufferedInputStream(new FileInputStream(source));
            var buffer = new byte[1024];
            while (true) {
                var count = in.read(buffer);
                if (count == -1) break;
                target.write(buffer, 0, count);
            }
            target.closeEntry();

        } finally {
            if (in != null) {
                in.close();
            }
        }
    }
}