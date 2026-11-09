package com.chayitong.media;

import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.exceptions.BusinessException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
public class MediaInspector {

    public InspectedMedia inspect(Path file, String originalFilename, String multipartMimeType, String purpose) {
        byte[] header = readHeader(file);
        String mimeType = detectMimeType(header);
        if (mimeType == null || !extensionMatches(originalFilename, mimeType)
                || !mimeType.equalsIgnoreCase(multipartMimeType)) {
            throw invalid();
        }
        if ("video/mp4".equals(mimeType)) {
            if (!"post".equals(purpose)) {
                throw invalid();
            }
            return inspectMp4(file);
        }
        if ("image/webp".equals(mimeType)) {
            return inspectWebp(file);
        }
        try {
            BufferedImage image = ImageIO.read(file.toFile());
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw invalid();
            }
            return new InspectedMedia("IMAGE", mimeType, image.getWidth(), image.getHeight(), null);
        } catch (IOException exception) {
            throw invalid();
        }
    }

    private byte[] readHeader(Path file) {
        byte[] header = new byte[16];
        try (InputStream input = Files.newInputStream(file)) {
            int offset = 0;
            while (offset < header.length) {
                int count = input.read(header, offset, header.length - offset);
                if (count < 0) {
                    break;
                }
                offset += count;
            }
            return java.util.Arrays.copyOf(header, offset);
        } catch (IOException exception) {
            throw invalid();
        }
    }

    private String detectMimeType(byte[] bytes) {
        if (startsWith(bytes, 0, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(bytes, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (startsWithAscii(bytes, 0, "RIFF") && startsWithAscii(bytes, 8, "WEBP")) {
            return "image/webp";
        }
        if (startsWithAscii(bytes, 4, "ftyp")) {
            return "video/mp4";
        }
        return null;
    }

    private boolean extensionMatches(String filename, String mimeType) {
        if (filename == null) {
            return false;
        }
        String name = Path.of(filename).getFileName().toString().toLowerCase(Locale.ROOT);
        return switch (mimeType) {
            case "image/jpeg" -> name.endsWith(".jpg") || name.endsWith(".jpeg");
            case "image/png" -> name.endsWith(".png");
            case "image/webp" -> name.endsWith(".webp");
            case "video/mp4" -> name.endsWith(".mp4");
            default -> false;
        };
    }

    private InspectedMedia inspectWebp(Path file) {
        try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
            long length = input.length();
            input.seek(4);
            if (length < 20 || (Integer.toUnsignedLong(Integer.reverseBytes(input.readInt())) + 8) != length) {
                throw invalid();
            }
            input.seek(12);
            while (input.getFilePointer() + 8 <= length) {
                int chunkType = input.readInt();
                long chunkSize = Integer.toUnsignedLong(Integer.reverseBytes(input.readInt()));
                long dataOffset = input.getFilePointer();
                if (chunkSize <= 0 || dataOffset + chunkSize > length) {
                    throw invalid();
                }
                if (chunkType == fourCc("VP8X")) {
                    if (chunkSize < 10) {
                        throw invalid();
                    }
                    byte[] data = new byte[10];
                    input.readFully(data);
                    int width = 1 + uint24(data, 4);
                    int height = 1 + uint24(data, 7);
                    return new InspectedMedia("IMAGE", "image/webp", width, height, null);
                }
                if (chunkType == fourCc("VP8 ")) {
                    if (chunkSize < 10) {
                        throw invalid();
                    }
                    byte[] data = new byte[10];
                    input.readFully(data);
                    if ((data[3] & 0xFF) != 0x9D || (data[4] & 0xFF) != 0x01 || (data[5] & 0xFF) != 0x2A) {
                        throw invalid();
                    }
                    int width = littleEndian16(data, 6) & 0x3FFF;
                    int height = littleEndian16(data, 8) & 0x3FFF;
                    if (width == 0 || height == 0) {
                        throw invalid();
                    }
                    return new InspectedMedia("IMAGE", "image/webp", width, height, null);
                }
                if (chunkType == fourCc("VP8L")) {
                    byte[] data = new byte[5];
                    input.readFully(data);
                    if ((data[0] & 0xFF) != 0x2F) {
                        throw invalid();
                    }
                    int width = 1 + ((data[1] & 0xFF) | ((data[2] & 0x3F) << 8));
                    int height = 1 + (((data[2] & 0xFF) >> 6) | ((data[3] & 0xFF) << 2)
                            | ((data[4] & 0x0F) << 10));
                    return new InspectedMedia("IMAGE", "image/webp", width, height, null);
                }
                input.seek(dataOffset + chunkSize + (chunkSize % 2));
            }
            throw invalid();
        } catch (IOException exception) {
            throw invalid();
        }
    }

    private InspectedMedia inspectMp4(Path file) {
        try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
            long fileLength = input.length();
            long position = 0;
            boolean fileTypeSeen = false;
            Integer duration = null;
            while (position + 8 <= fileLength) {
                Box box = readBox(input, position, fileLength);
                if (box.type() == fourCc("ftyp")) {
                    fileTypeSeen = true;
                } else if (box.type() == fourCc("moov")) {
                    duration = readMovieDuration(input, box.dataOffset(), position + box.size());
                }
                position += box.size();
            }
            if (!fileTypeSeen || position != fileLength || duration == null) {
                throw invalid();
            }
            return new InspectedMedia("VIDEO", "video/mp4", null, null, duration);
        } catch (IOException exception) {
            throw invalid();
        }
    }

    private Integer readMovieDuration(RandomAccessFile input, long start, long end) throws IOException {
        long position = start;
        while (position + 8 <= end) {
            Box box = readBox(input, position, end);
            if (box.type() == fourCc("mvhd")) {
                input.seek(box.dataOffset());
                int version = input.readUnsignedByte();
                input.skipBytes(3);
                long timescale;
                long duration;
                if (version == 0) {
                    input.skipBytes(8);
                    timescale = Integer.toUnsignedLong(input.readInt());
                    duration = Integer.toUnsignedLong(input.readInt());
                } else if (version == 1) {
                    input.skipBytes(16);
                    timescale = Integer.toUnsignedLong(input.readInt());
                    duration = input.readLong();
                } else {
                    throw invalid();
                }
                if (timescale <= 0 || duration <= 0 || duration > timescale * 60) {
                    throw invalid();
                }
                return Math.max(1, (int) Math.ceil(duration * 1000.0 / timescale));
            }
            position += box.size();
        }
        return null;
    }

    private Box readBox(RandomAccessFile input, long position, long limit) throws IOException {
        input.seek(position);
        long size = Integer.toUnsignedLong(input.readInt());
        int type = input.readInt();
        long headerSize = 8;
        if (size == 1) {
            if (position + 16 > limit) {
                throw invalid();
            }
            size = input.readLong();
            headerSize = 16;
        } else if (size == 0) {
            size = limit - position;
        }
        if (size < headerSize || size > limit - position) {
            throw invalid();
        }
        return new Box(type, size, position + headerSize);
    }

    private int uint24(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8) | ((bytes[offset + 2] & 0xFF) << 16);
    }

    private int littleEndian16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    private boolean startsWith(byte[] bytes, int offset, int... expected) {
        if (bytes.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean startsWithAscii(byte[] bytes, int offset, String expected) {
        byte[] expectedBytes = expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        for (int i = 0; i < expectedBytes.length; i++) {
            if (bytes.length <= offset + i || bytes[offset + i] != expectedBytes[i]) {
                return false;
            }
        }
        return true;
    }

    private int fourCc(String value) {
        return (value.charAt(0) << 24) | (value.charAt(1) << 16) | (value.charAt(2) << 8) | value.charAt(3);
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_FILE);
    }

    private record Box(int type, long size, long dataOffset) {
    }
}
