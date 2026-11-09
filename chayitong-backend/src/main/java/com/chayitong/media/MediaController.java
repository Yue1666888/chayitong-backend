package com.chayitong.media;

import com.chayitong.auth.CurrentUser;
import com.chayitong.common.api.CommonResponse;
import java.io.InputStream;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping
    public CommonResponse<MediaResponse> upload(@RequestParam("file") MultipartFile file,
            @RequestParam("purpose") String purpose) {
        return CommonResponse.success(mediaService.upload(CurrentUser.require(), file, purpose));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<StreamingResponseBody> content(@PathVariable("id") String id) {
        MediaContent content = mediaService.content(id);
        if (content.externalUrl() != null) {
            return ResponseEntity.status(302).location(URI.create(content.externalUrl())).build();
        }
        StreamingResponseBody body = output -> {
            try (InputStream input = content.inputStream()) {
                input.transferTo(output);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .contentLength(content.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(body);
    }
}
