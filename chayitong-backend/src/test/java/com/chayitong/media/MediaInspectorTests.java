package com.chayitong.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chayitong.common.exceptions.BusinessException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaInspectorTests {

    @TempDir
    Path directory;

    private final MediaInspector inspector = new MediaInspector();

    @Test
    void inspectsJpegPngAndWebpDimensions() throws Exception {
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        for (String format : new String[]{"png", "jpg"}) {
            Path file = directory.resolve("image." + format);
            ImageIO.write(image, format, file.toFile());
            InspectedMedia result = inspector.inspect(file, file.getFileName().toString(),
                    "jpg".equals(format) ? "image/jpeg" : "image/png", "avatar");
            assertThat(result.width()).isEqualTo(3);
            assertThat(result.height()).isEqualTo(2);
        }
        Path webp = directory.resolve("image.webp");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write("RIFF".getBytes());
        writeIntLittleEndian(output, 22);
        output.write("WEBPVP8X".getBytes());
        writeIntLittleEndian(output, 10);
        output.write(new byte[4]);
        output.write(new byte[]{2, 0, 0, 1, 0, 0});
        Files.write(webp, output.toByteArray());
        InspectedMedia result = inspector.inspect(webp, "image.webp", "image/webp", "avatar");
        assertThat(result.width()).isEqualTo(3);
        assertThat(result.height()).isEqualTo(2);
    }

    @Test
    void rejectsCorruptedFilesAndVideosOverSixtySeconds() throws Exception {
        Path corrupt = directory.resolve("bad.png");
        Files.write(corrupt, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        assertThatThrownBy(() -> inspector.inspect(corrupt, "bad.png", "image/png", "avatar"))
                .isInstanceOf(BusinessException.class);
        Path video = directory.resolve("long.mp4");
        Files.write(video, mp4(61));
        assertThatThrownBy(() -> inspector.inspect(video, "long.mp4", "video/mp4", "post"))
                .isInstanceOf(BusinessException.class);
    }

    private byte[] mp4(int seconds) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeBox(output, "ftyp", "isom".getBytes(), new byte[4], "isommp42".getBytes());
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(new byte[12]);
        writeInt(payload, 1000);
        writeInt(payload, seconds * 1000);
        payload.write(new byte[80]);
        ByteArrayOutputStream mvhd = new ByteArrayOutputStream();
        writeBox(mvhd, "mvhd", payload.toByteArray());
        writeBox(output, "moov", mvhd.toByteArray());
        return output.toByteArray();
    }

    private void writeBox(ByteArrayOutputStream output, String type, byte[]... payloads) throws Exception {
        int size = 8;
        for (byte[] payload : payloads) {
            size += payload.length;
        }
        writeInt(output, size);
        output.write(type.getBytes());
        for (byte[] payload : payloads) {
            output.write(payload);
        }
    }

    private void writeInt(ByteArrayOutputStream output, int value) {
        output.write((value >>> 24) & 0xFF);
        output.write((value >>> 16) & 0xFF);
        output.write((value >>> 8) & 0xFF);
        output.write(value & 0xFF);
    }

    private void writeIntLittleEndian(ByteArrayOutputStream output, int value) {
        output.write(value & 0xFF);
        output.write((value >>> 8) & 0xFF);
        output.write((value >>> 16) & 0xFF);
        output.write((value >>> 24) & 0xFF);
    }
}
