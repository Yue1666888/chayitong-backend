package com.chayitong.community;

import java.time.LocalDateTime;

final class CommunityRecords {

    private CommunityRecords() {
    }

    record Post(
            long id,
            String businessKey,
            long authorId,
            String clientRequestId,
            String requestHash,
            String content,
            String topic,
            String tags,
            Long relatedTeaId,
            String status,
            long likeCount,
            long commentCount,
            LocalDateTime publishedAt,
            LocalDateTime createdAt,
            int deleted) {
    }

    record Comment(
            long id,
            String businessKey,
            long postId,
            long authorId,
            String clientRequestId,
            String requestHash,
            String content,
            LocalDateTime createdAt,
            int deleted) {
    }

    record Author(
            long id,
            String businessKey,
            String displayName,
            Long avatarMediaId,
            long points) {
    }

    record Media(
            long id,
            String mediaId,
            long ownerUserId,
            String purpose,
            String mediaType,
            String storageProvider,
            String externalUrl,
            String status,
            String boundType,
            String boundId,
            int deleted) {
    }

    record PostMedia(String mediaRole, int sortOrder, Media media) {
    }

    record Tea(
            long id,
            String businessKey,
            String slug,
            String name,
            String aliases,
            String category,
            String originProvince,
            String originCity,
            String originDistrict,
            String summary,
            Long coverMediaId,
            String coverUrl,
            boolean featured,
            int sort) {
    }

    record Like(long id, long postId, long userId, int deleted) {
    }
}
