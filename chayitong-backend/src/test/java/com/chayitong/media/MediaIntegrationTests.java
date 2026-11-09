package com.chayitong.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chayitong.AbstractIntegrationTest;
import com.chayitong.auth.WechatLoginClient;
import com.chayitong.common.exceptions.BusinessException;
import com.chayitong.config.ChayitongProperties;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class MediaIntegrationTests extends AbstractIntegrationTest {

    @MockitoBean
    private WechatLoginClient wechatLoginClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MediaService mediaService;

    @Test
    void validImageCanBeUploadedBoundAndReadPublicly() throws Exception {
        String token = login("media-one", "media-openid-one");
        MockMultipartFile file = new MockMultipartFile("file", "../../avatar.png", "image/png", png());
        String mediaId = mediaId(upload(token, file, "avatar")
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.mediaType").value("image"))
                .andExpect(jsonPath("$.data.mimeType").value("image/png"))
                .andExpect(jsonPath("$.data.width").value(2))
                .andExpect(jsonPath("$.data.height").value(1)));
        String objectKey = jdbcTemplate.queryForObject("SELECT object_key FROM media_file WHERE media_id = ?",
                String.class, mediaId);
        assertThat(objectKey).doesNotContain("avatar").doesNotContain("..");

        mockMvc.perform(get("/api/media/{id}/content", mediaId))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("image/png"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, file.getSize()))
                .andExpect(content().bytes(file.getBytes()));
        mockMvc.perform(patch("/api/me/profile").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"avatar\":\"" + mediaId + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.avatar").isString());
        mockMvc.perform(patch("/api/me/profile").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"avatar\":\"" + mediaId + "\"}"))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM media_file WHERE media_id = ?", String.class, mediaId))
                .isEqualTo("BOUND");
    }

    @Test
    void uploadRejectsAnonymousAdminPurposeForgedAndOversizedFiles() throws Exception {
        MockMultipartFile valid = new MockMultipartFile("file", "image.png", "image/png", png());
        mockMvc.perform(multipart("/api/media").file(valid).param("purpose", "avatar"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        String token = login("media-security", "media-openid-security");
        upload(token, valid, "content").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        long before = countMedia();
        MockMultipartFile forged = new MockMultipartFile("file", "fake.jpg", "image/jpeg", "<html>bad</html>".getBytes());
        upload(token, forged, "avatar").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE"));
        MockMultipartFile oversized = new MockMultipartFile("file", "large.png", "image/png",
                new byte[5 * 1024 * 1024 + 1]);
        upload(token, oversized, "avatar").andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
        assertThat(countMedia()).isEqualTo(before);
    }

    @Test
    void mediaOwnershipPurposeAndBoundStateAreEnforced() throws Exception {
        String owner = login("media-owner", "media-openid-owner");
        String other = login("media-other", "media-openid-other");
        String avatarId = mediaId(upload(owner, new MockMultipartFile("file", "avatar.png", "image/png", png()), "avatar")
                .andExpect(status().isOk()));
        mockMvc.perform(patch("/api/me/profile").header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"avatar\":\"" + avatarId + "\"}"))
                .andExpect(status().isForbidden());
        String postId = mediaId(upload(owner, new MockMultipartFile("file", "post.png", "image/png", png()), "post")
                .andExpect(status().isOk()));
        mockMvc.perform(patch("/api/me/profile").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"avatar\":\"" + postId + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/me/profile").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"avatar\":\"" + avatarId + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/me/profile").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"avatar\":\"wxfile://tmp/avatar.png\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void externalMediaUsesDirectUrlAndControlledHttpsRedirect() throws Exception {
        login("media-external", "media-openid-external");
        Long ownerId = jdbcTemplate.queryForObject("SELECT user_id FROM wechat_identity WHERE openid = ?",
                Long.class, "media-openid-external");
        long id = 910000000000000001L;
        String mediaId = "external-media-test";
        String externalUrl = "https://example.cos.ap-beijing.myqcloud.com/avatar.jpg";
        jdbcTemplate.update("INSERT INTO media_file (id,media_id,owner_user_id,purpose,media_type,storage_provider,"
                        + "object_key,external_url,mime_type,size_bytes,sha256,status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                id, mediaId, ownerId, "avatar", "IMAGE", "EXTERNAL", mediaId, externalUrl, "image/jpeg", 1,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", "BOUND");
        assertThat(mediaService.urlFor(id)).isEqualTo(externalUrl);
        mockMvc.perform(get("/api/media/{id}/content", mediaId))
                .andExpect(status().isFound()).andExpect(header().string(HttpHeaders.LOCATION, externalUrl));
    }

    @Test
    void mp4DurationIsReadFromFileAndS3DoesNotFallBackToLocal() throws Exception {
        String token = login("media-video", "media-openid-video");
        String mediaId = mediaId(upload(token, new MockMultipartFile("file", "clip.mp4", "video/mp4", mp4(2)), "post")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.mediaType").value("video"))
                .andExpect(jsonPath("$.data.durationSeconds").value(2)));
        mockMvc.perform(get("/api/media/{id}/content", mediaId)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("video/mp4"));

        var s3 = new ChayitongProperties.S3("", "", "", "", "");
        var media = new ChayitongProperties.Media("s3", null, "http://localhost/api/media", s3, null);
        var properties = new ChayitongProperties(null, null, null, media, null);
        Throwable failure = catchThrowable(() -> new S3MediaStorage(properties).bucket());
        assertThat(failure).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) failure).getErrorCode().getStatus().value()).isEqualTo(503);
    }

    private org.springframework.test.web.servlet.ResultActions upload(String token, MockMultipartFile file, String purpose)
            throws Exception {
        return mockMvc.perform(multipart("/api/media").file(file).param("purpose", purpose)
                .header("Authorization", bearer(token)));
    }

    private String mediaId(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString())
                .at("/data/mediaId").asText();
    }

    private String login(String code, String openid) throws Exception {
        when(wechatLoginClient.login(code)).thenReturn(new WechatLoginClient.WechatIdentityData(openid, null));
        String body = mockMvc.perform(post("/api/auth/wechat/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/accessToken").asText();
    }

    private byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x336699);
        image.setRGB(1, 0, 0x996633);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private byte[] mp4(int seconds) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeBox(output, "ftyp", "isom".getBytes(), new byte[4], "isommp42".getBytes());
        ByteArrayOutputStream mvhdPayload = new ByteArrayOutputStream();
        mvhdPayload.write(new byte[12]);
        writeInt(mvhdPayload, 1000);
        writeInt(mvhdPayload, seconds * 1000);
        mvhdPayload.write(new byte[80]);
        ByteArrayOutputStream mvhd = new ByteArrayOutputStream();
        writeBox(mvhd, "mvhd", mvhdPayload.toByteArray());
        ByteArrayOutputStream moov = new ByteArrayOutputStream();
        writeBox(moov, "moov", mvhd.toByteArray());
        output.write(moov.toByteArray());
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

    private long countMedia() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM media_file", Long.class);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
