// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.main.decompiler;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * File saver supporting both, Threaded saving and 'SingleFile' mode.
 */
//TODO, Split off default impl inside ConsoleDecompiler and make this extend that.
public class ThreadSafeResultSaver implements IResultSaver {

  private final Map<String, ArchiveContext> archiveContexts = new HashMap<>();
  private final File target;
  private final boolean archiveMode;//Latch for Archive mode.
  private ArchiveContext singeArchiveCtx;

  public ThreadSafeResultSaver(File target) {
    this.target = target;
    this.archiveMode = !target.isDirectory();
  }

  private ArchiveContext getCtx(String path) {
    if (archiveMode) {
      return singeArchiveCtx;
    }
    return archiveContexts.get(path);
  }

  @Override
  public void createArchive(String path, String archiveName, Manifest manifest) {
    if (archiveMode && singeArchiveCtx != null) {
      throw new UnsupportedOperationException("Attempted to write multiple archives at the same time.");
    }
    File file = archiveMode ? target : new File(getAbsolutePath(path), archiveName);
    ArchiveContext ctx = getCtx(file.getPath());
    if (ctx != null) {
      throw new RuntimeException("Archive already open for: " + file);
    }
    try {
      if (!(file.createNewFile() || file.isFile())) {
        throw new IOException("Cannot create file " + file);
      }
      FileOutputStream fos = new FileOutputStream(file);
      ZipOutputStream zos = manifest != null ? new JarOutputStream(fos, manifest) : new ZipOutputStream(fos);
      ctx = new ArchiveContext(file, zos);
      if (archiveMode) {
        singeArchiveCtx = ctx;
      } else {
        archiveContexts.put(file.getPath(), ctx);
      }
    } catch (IOException e) {
      DecompilerContext.getLogger().writeMessage("Cannot create archive " + file, e);
    }
  }

  @Override
  public void saveDirEntry(String path, String archiveName, String entryName) {
    saveClassEntry(path, archiveName, null, entryName, null);
  }

  @Override
  public void copyEntry(String source, String path, String archiveName, String entryName) {
    String file = new File(getAbsolutePath(path), archiveName).getPath();
    ArchiveContext ctx = getCtx(file);
    if (ctx == null) {
      throw new RuntimeException("Archive closed and tried to copy entry '" + entryName + "' from '" + source + "' to '" + file + "'.");
    }
    ctx.submit(() -> {
      if (!ctx.addEntry(entryName)) {
        return;
      }
      try (ZipFile srcArchive = new ZipFile(new File(source))) {
        ZipEntry entry = srcArchive.getEntry(entryName);
        if (entry != null) {
          try (InputStream in = srcArchive.getInputStream(entry)) {
            ctx.stream.putNextEntry(new ZipEntry(entryName));
            InterpreterUtil.copyStream(in, ctx.stream);
          }
        }
      } catch (IOException e) {
        DecompilerContext.getLogger().writeMessage("Cannot copy entry " + entryName + " from " + source + " to " + file, e);
      }
    });
  }

  @Override
  public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) {
    String file = new File(getAbsolutePath(path), archiveName).getPath();
    ArchiveContext ctx = getCtx(file);
    if (ctx == null) {
      throw new RuntimeException("Archive closed and tried to write entry '" + entryName + "' to '" + file + "'.");
    }
    ctx.submit(() -> {
      if (!ctx.addEntry(entryName)) {
        return;
      }
      try {
        ctx.stream.putNextEntry(new ZipEntry(entryName));
        if (content != null) {
          ctx.stream.write(content.getBytes(StandardCharsets.UTF_8));
        }
      } catch (IOException e) {
        DecompilerContext.getLogger().writeMessage("Cannot write entry " + entryName + " to " + file, e);
      }
    });
  }

  @Override
  public void closeArchive(String path, String archiveName) {
    String file = new File(getAbsolutePath(path), archiveName).getPath();
    ArchiveContext ctx = getCtx(file);
    if (ctx == null) {
      throw new RuntimeException("Tried to close closed archive '" + file + "'.");
    }
    //Submit a job at the end of the executor.
    Future<?> closeFuture = ctx.submit(() -> {
      try {
        ctx.stream.close();
      } catch (IOException e) {
        DecompilerContext.getLogger().writeMessage("Cannot close " + file, IFernflowerLogger.Severity.WARN, e);
      }
    });

    //Ask the executor to shutdown gracefully.
    ctx.executor.shutdown();

    try {
      //Wait for our future to execute.
      closeFuture.get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
    if (archiveMode) {
      singeArchiveCtx = null;
    } else {
      //We are done.
      archiveContexts.remove(file);
    }
  }

  @Override
  public void saveFolder(String path) {
    if (archiveMode) {
      if (!"".equals(path)) {
        throw new UnsupportedOperationException("Targeted a single output, but tried to create a directory");
      }
      return;
    }
    File dir = new File(getAbsolutePath(path));
    if (!(dir.mkdirs() || dir.isDirectory())) {
      throw new RuntimeException("Cannot create directory " + dir);
    }
  }

  @Override
  public void copyFile(String source, String path, String entryName) {
    if (archiveMode) {
      throw new UnsupportedOperationException("Targeted a single output, but tried to copy file");
    }
    try {
      InterpreterUtil.copyFile(new File(source), new File(getAbsolutePath(path), entryName));
    } catch (IOException ex) {
      DecompilerContext.getLogger().writeMessage("Cannot copy " + source + " to " + entryName, ex);
    }
  }

  @Override
  public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
    if (archiveMode) {
      throw new UnsupportedOperationException("Targeted a single output, but tried to save a class file");
    }
    File file = new File(getAbsolutePath(path), entryName);
    try (Writer out = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
      out.write(content);
    } catch (IOException ex) {
      DecompilerContext.getLogger().writeMessage("Cannot write class file " + file, ex);
    }
  }

  private String getAbsolutePath(String path) {
    return new File(target, path).getAbsolutePath();
  }

  private static class ArchiveContext {

    public final File file;
    public final ZipOutputStream stream;
    public final ExecutorService executor = Executors.newSingleThreadExecutor();
    public final Set<String> savedEntries = new HashSet<>();

    private ArchiveContext(File file, ZipOutputStream stream) {
      this.file = file;
      this.stream = stream;
    }

    public Future<?> submit(Runnable runnable) {
      return executor.submit(runnable);
    }

    public boolean addEntry(String entryName) {
      boolean added = savedEntries.add(entryName);
      if (!added) {
        DecompilerContext.getLogger().writeMessage("Zip entry " + entryName + " already exists in " + file, IFernflowerLogger.Severity.WARN);
      }
      return added;
    }
  }
}
