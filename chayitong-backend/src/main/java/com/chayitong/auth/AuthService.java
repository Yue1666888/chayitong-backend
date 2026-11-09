package com.chayitong.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.exceptions.BusinessException;
import com.chayitong.config.ChayitongProperties;
import com.chayitong.user.UserEntity;
import com.chayitong.user.UserMapper;
import com.chayitong.user.UserProfileService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthService implements AccessTokenAuthenticator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final WechatLoginClient wechatLoginClient;
    private final WechatIdentityMapper identityMapper;
    private final UserMapper userMapper;
    private final AuthSessionMapper sessionMapper;
    private final UserProfileService userProfileService;
    private final ChayitongProperties properties;
    private final TransactionTemplate transactionTemplate;

    public AuthService(WechatLoginClient wechatLoginClient, WechatIdentityMapper identityMapper,
            UserMapper userMapper, AuthSessionMapper sessionMapper, UserProfileService userProfileService,
            ChayitongProperties properties, PlatformTransactionManager transactionManager) {
        this.wechatLoginClient = wechatLoginClient;
        this.identityMapper = identityMapper;
        this.userMapper = userMapper;
        this.sessionMapper = sessionMapper;
        this.userProfileService = userProfileService;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public TokenResponse login(String code) {
        String normalizedCode = code == null ? "" : code.trim();
        if (normalizedCode.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR);
        }
        WechatLoginClient.WechatIdentityData identityData = wechatLoginClient.login(normalizedCode);
        if (identityData == null || identityData.openid() == null || identityData.openid().isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        try {
            return transactionTemplate.execute(status -> completeLogin(identityData, false));
        } catch (DuplicateKeyException exception) {
            return transactionTemplate.execute(status -> completeLogin(identityData, true));
        }
    }

    @Override
    public AuthenticatedUser authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        AuthSession session = sessionMapper.selectOne(Wrappers.<AuthSession>lambdaQuery()
                .eq(AuthSession::getAccessTokenHash, sha256(rawToken)));
        if (session == null || session.getRevokedAt() != null
                || !session.getAccessExpiresAt().isAfter(utcNow())) {
            return null;
        }
        UserEntity user = userMapper.selectById(session.getUserId());
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            return null;
        }
        return new AuthenticatedUser(user.getId(), user.getBusinessKey(), user.getRole(), session.getId());
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        AuthSession session = sessionMapper.lockByRefreshTokenHash(sha256(rawRefreshToken));
        if (session == null || session.getRevokedAt() != null
                || !session.getRefreshExpiresAt().isAfter(utcNow())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        UserEntity user = userMapper.selectById(session.getUserId());
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        session.setRotatedAt(utcNow());
        return createTokenResponse(user, session);
    }

    @Transactional
    public void logout(AuthenticatedUser user) {
        AuthSession session = sessionMapper.selectById(user.sessionId());
        if (session == null || session.getRevokedAt() != null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        session.setRevokedAt(utcNow());
        sessionMapper.updateById(session);
    }

    private TokenResponse completeLogin(WechatLoginClient.WechatIdentityData identityData, boolean currentRead) {
        String appid = properties.wechat().appId();
        WechatIdentity identity = currentRead
                ? identityMapper.lockByAppidAndOpenid(appid, identityData.openid())
                : identityMapper.selectOne(Wrappers.<WechatIdentity>lambdaQuery()
                        .eq(WechatIdentity::getAppid, appid)
                        .eq(WechatIdentity::getOpenid, identityData.openid()));
        if (currentRead && identity == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "微信身份映射不可用");
        }
        if (identity != null && identity.getDeleted() != 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        UserEntity user;
        LocalDateTime now = utcNow();
        if (identity == null) {
            user = new UserEntity();
            user.setBusinessKey(UUID.randomUUID().toString());
            user.setDisplayName("茶友");
            user.setRole("USER");
            user.setPointsBalance(0L);
            user.setStatus("ACTIVE");
            userMapper.insert(user);
            identity = new WechatIdentity();
            identity.setUserId(user.getId());
            identity.setAppid(appid);
            identity.setOpenid(identityData.openid());
            identity.setUnionid(identityData.unionid());
            identity.setLastLoginAt(now);
            identityMapper.insert(identity);
        } else {
            user = userMapper.selectById(identity.getUserId());
            if (user == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            identity.setLastLoginAt(now);
            identityMapper.updateById(identity);
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return createTokenResponse(user, null);
    }

    private TokenResponse createTokenResponse(UserEntity user, AuthSession session) {
        long accessTtl = properties.token().accessTtlSeconds();
        long refreshTtl = properties.token().refreshTtlSeconds();
        if (accessTtl <= 0 || refreshTtl <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "令牌有效期配置错误");
        }
        String accessToken = randomToken();
        String refreshToken = randomToken();
        LocalDateTime now = utcNow();
        if (session == null) {
            session = new AuthSession();
            session.setUserId(user.getId());
            session.setAccessTokenHash(sha256(accessToken));
            session.setRefreshTokenHash(sha256(refreshToken));
            session.setAccessExpiresAt(now.plusSeconds(accessTtl));
            session.setRefreshExpiresAt(now.plusSeconds(refreshTtl));
            sessionMapper.insert(session);
        } else {
            session.setAccessTokenHash(sha256(accessToken));
            session.setRefreshTokenHash(sha256(refreshToken));
            session.setAccessExpiresAt(now.plusSeconds(accessTtl));
            session.setRefreshExpiresAt(now.plusSeconds(refreshTtl));
            sessionMapper.updateById(session);
        }
        return new TokenResponse(accessToken, refreshToken, "Bearer", accessTtl, refreshTtl,
                userProfileService.profile(user));
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
