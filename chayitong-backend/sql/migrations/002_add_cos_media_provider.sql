USE chayitong;

ALTER TABLE media_file DROP CONSTRAINT chk_media_file_provider;
ALTER TABLE media_file
    ADD CONSTRAINT chk_media_file_provider
    CHECK (storage_provider IN ('LOCAL', 'S3', 'COS', 'EXTERNAL'));
