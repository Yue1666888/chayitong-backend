package com.chayitong.media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public interface MediaStorage {

    String provider();

    String bucket();

    void put(Path source, String objectKey, String mimeType) throws IOException;

    InputStream open(String objectKey) throws IOException;

    void delete(String objectKey);
}
