package com.chayitong.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chayitong.common.exceptions.BusinessException;
import com.chayitong.config.ChayitongProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class CosMediaStorageTests {

    @Test
    void usesConfiguredBucketPrefixAndStreamsContent() throws Exception {
        COSClient client = mock(COSClient.class);
        COSObject object = new COSObject();
        object.setObjectContent(new COSObjectInputStream(new ByteArrayInputStream("content".getBytes()), null));
        when(client.getObject(any(com.qcloud.cos.model.GetObjectRequest.class))).thenReturn(object);
        var storage = new CosMediaStorage(configuration("media/"), client);

        var path = Files.createTempFile("cos-test", ".png");
        Files.writeString(path, "image");
        storage.put(path, "object.png", "image/png");

        var put = org.mockito.ArgumentCaptor.forClass(com.qcloud.cos.model.PutObjectRequest.class);
        verify(client).putObject(put.capture());
        assertThat(put.getValue().getBucketName()).isEqualTo("bucket-123");
        assertThat(put.getValue().getKey()).isEqualTo("media/object.png");
        assertThat(storage.open("object.png").readAllBytes()).isEqualTo("content".getBytes());
        storage.delete("object.png");
        verify(client).deleteObject("bucket-123", "media/object.png");
        Files.deleteIfExists(path);
    }

    @Test
    void incompleteConfigurationFailsWithoutFallingBack() {
        var storage = new CosMediaStorage(new ChayitongProperties.Cos("", null, "", "", ""), mock(COSClient.class));
        assertThatThrownBy(storage::bucket).isInstanceOf(BusinessException.class)
                .matches(error -> ((BusinessException) error).getErrorCode().getStatus().value() == 503);
    }

    @Test
    void mediaServiceSelectsCosProvider() {
        MediaStorage cosStorage = mock(MediaStorage.class);
        when(cosStorage.provider()).thenReturn("COS");
        var cos = configuration("media/");
        var media = new ChayitongProperties.Media("cos", null, "http://localhost/api/media", null, cos);
        var properties = new ChayitongProperties(null, null, null, media, null);
        var service = new MediaService(null, null, properties, java.util.List.of(cosStorage),
                mock(org.springframework.core.env.Environment.class));

        MediaStorage selected = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "storage");

        assertThat(selected).isSameAs(cosStorage);
    }

    private ChayitongProperties.Cos configuration(String prefix) {
        return new ChayitongProperties.Cos("id", "secret", "ap-beijing", "bucket-123", prefix);
    }
}
