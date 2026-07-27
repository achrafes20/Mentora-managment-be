package ma.hbdev.rh.document;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.lang.NonNull;
import org.springframework.web.multipart.MultipartFile;

class ByteArrayMultipartFile implements MultipartFile {

  private final byte[] content;
  private final String name;
  private final String originalFilename;
  private final String contentType;

  ByteArrayMultipartFile(byte[] content, String name, String originalFilename, String contentType) {
    this.content = content;
    this.name = name;
    this.originalFilename = originalFilename;
    this.contentType = contentType;
  }

  @Override
  @NonNull
  public String getName() {
    return name;
  }

  @Override
  public String getOriginalFilename() {
    return originalFilename;
  }

  @Override
  public String getContentType() {
    return contentType;
  }

  @Override
  public boolean isEmpty() {
    return content == null || content.length == 0;
  }

  @Override
  public long getSize() {
    return content != null ? content.length : 0;
  }

  @Override
  @NonNull
  public byte[] getBytes() {
    return content;
  }

  @Override
  @NonNull
  public InputStream getInputStream() {
    return new ByteArrayInputStream(content);
  }

  @Override
  public void transferTo(@NonNull File dest) throws IOException, IllegalStateException {
    java.nio.file.Files.write(dest.toPath(), content);
  }
}
