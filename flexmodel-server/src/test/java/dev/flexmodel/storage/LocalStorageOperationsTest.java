package dev.flexmodel.storage;

import dev.flexmodel.storage.FileItem;
import dev.flexmodel.storage.StorageOperations;
import dev.flexmodel.storage.config.LocalStorageOperations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author cjbi
 */
public class LocalStorageOperationsTest {

  private StorageOperations storage;
  private Path testDir;

  @BeforeEach
  void setUp() throws IOException {
    testDir = Files.createTempDirectory("flexmodel-storage-test");
    storage = new LocalStorageOperations(testDir.toString());
  }

  @AfterEach
  void tearDown() throws IOException {
    if (Files.exists(testDir)) {
      Files.walk(testDir)
        .sorted(Comparator.reverseOrder())
        .forEach(p -> {
          try {
            Files.deleteIfExists(p);
          } catch (IOException ignored) {
          }
        });
    }
  }

  @Test
  void testUploadFile() throws IOException {
    String content = "Hello, Flexmodel!";
    byte[] bytes = content.getBytes();
    storage.uploadFile("/test.txt", new ByteArrayInputStream(bytes), bytes.length);

    FileItem file = storage.getFile("/test.txt");
    assertNotNull(file);
    assertEquals(bytes.length, file.getSize());
  }

  @Test
  void testUploadFileInFolder() throws IOException {
    // Create folder via empty object marker
    storage.uploadFile("/uploads/", new ByteArrayInputStream(new byte[0]), 0);
    String content = "Uploaded content";
    byte[] bytes = content.getBytes();
    storage.uploadFile("/uploads/doc.txt", new ByteArrayInputStream(bytes), bytes.length);

    FileItem file = storage.getFile("/uploads/doc.txt");
    assertNotNull(file);
  }

  @Test
  void testUploadEmptyObject() {
    // Folder marker: PUT empty object with trailing slash
    storage.uploadFile("/my-folder/", new ByteArrayInputStream(new byte[0]), 0);

    FileItem item = storage.getFile("/my-folder");
    assertNotNull(item);
    assertEquals(FileItem.FileType.folder, item.getType());
  }

  @Test
  void testListFiles() {
    // Create folders via empty object markers
    storage.uploadFile("/folder1/", new ByteArrayInputStream(new byte[0]), 0);
    storage.uploadFile("/folder2/", new ByteArrayInputStream(new byte[0]), 0);
    storage.uploadFile("/readme.txt", new ByteArrayInputStream("readme".getBytes()), 6);
    storage.uploadFile("/notes.txt", new ByteArrayInputStream("notes".getBytes()), 5);

    List<FileItem> files = storage.listFiles("");
    assertEquals(4, files.size());

    long folderCount = files.stream().filter(f -> f.getType() == FileItem.FileType.folder).count();
    long fileCount = files.stream().filter(f -> f.getType() == FileItem.FileType.file).count();
    assertEquals(2, folderCount);
    assertEquals(2, fileCount);
  }

  @Test
  void testListFilesInSubfolder() {
    storage.uploadFile("/sub/", new ByteArrayInputStream(new byte[0]), 0);
    storage.uploadFile("/sub/file1.txt", new ByteArrayInputStream("f1".getBytes()), 2);
    storage.uploadFile("/sub/file2.txt", new ByteArrayInputStream("f2".getBytes()), 2);

    List<FileItem> files = storage.listFiles("/sub");
    assertEquals(2, files.size());
    assertTrue(files.stream().allMatch(f -> f.getType() == FileItem.FileType.file));
  }

  @Test
  void testGetFile() {
    storage.uploadFile("/info.txt", new ByteArrayInputStream("info".getBytes()), 4);

    FileItem file = storage.getFile("/info.txt");
    assertNotNull(file);
    assertEquals("info.txt", file.getName());
    assertEquals(FileItem.FileType.file, file.getType());
    assertEquals(4, file.getSize());
    assertNotNull(file.getLastModified());
    assertEquals("info.txt", file.getPath());
  }

  @Test
  void testGetFolder() {
    storage.uploadFile("/mydir/", new ByteArrayInputStream(new byte[0]), 0);

    FileItem folder = storage.getFile("/mydir");
    assertNotNull(folder);
    assertEquals("mydir", folder.getName());
    assertEquals(FileItem.FileType.folder, folder.getType());
  }

  @Test
  void testGetNonExistentFile() {
    FileItem file = storage.getFile("/nonexistent.txt");
    assertNull(file);
  }

  @Test
  void testDeleteFile() {
    storage.uploadFile("/deleteme.txt", new ByteArrayInputStream("del".getBytes()), 3);
    assertNotNull(storage.getFile("/deleteme.txt"));

    storage.deleteFile("/deleteme.txt");
    assertNull(storage.getFile("/deleteme.txt"));
  }

  @Test
  void testDeleteFolder() {
    storage.uploadFile("/deldir/", new ByteArrayInputStream(new byte[0]), 0);
    storage.uploadFile("/deldir/f1.txt", new ByteArrayInputStream("f1".getBytes()), 2);
    storage.uploadFile("/deldir/f2.txt", new ByteArrayInputStream("f2".getBytes()), 2);

    assertNotNull(storage.getFile("/deldir"));
    storage.deleteFile("/deldir");
    assertNull(storage.getFile("/deldir"));
  }

  @Test
  void testGetInputStream() throws IOException {
    String content = "Download test content";
    storage.uploadFile("/download.txt", new ByteArrayInputStream(content.getBytes()), content.length());

    InputStream in = storage.getInputStream("/download.txt");
    byte[] buffer = new byte[1024];
    int read = in.read(buffer);
    in.close();

    String result = new String(buffer, 0, read);
    assertEquals(content, result);
  }

  @Test
  void testGetInputStreamFileNotFound() {
    assertThrows(RuntimeException.class, () -> storage.getInputStream("/no-file.txt"));
  }

  @Test
  void testPathTraversalPrevention() {
    assertThrows(SecurityException.class, () -> storage.uploadFile("/../escape.txt",
      new ByteArrayInputStream("escape".getBytes()), 6));
  }

  @Test
  void testListFilesNonExistentPath() {
    List<FileItem> files = storage.listFiles("/non-existent-path");
    assertTrue(files.isEmpty());
  }
}
